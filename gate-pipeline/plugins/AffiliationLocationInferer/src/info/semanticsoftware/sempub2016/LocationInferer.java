/*
 * Semantic Software Lab submission to Semantic Publishing Challenge 2016,
 * http://www.semanticsoftware.info/sempub-challenge-2016
 *
 * Copyright (c) 2016 Semantic Software Lab, http://www.semanticsoftware.info
 *    Rene Witte
 *    Bahar Sateli
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */

package info.semanticsoftware.sempub2016;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import gate.*;
import gate.creole.*;
import gate.creole.metadata.*;

/**
 * This class is the implementation of the resource AFFILIATIONLOCATIONINFERER.
 */
@CreoleResource(name = "AffiliationLocationInferer", comment = "Infers in which country an academic institute is located.")
public class LocationInferer extends AbstractLanguageAnalyser implements ProcessingResource {
	/**
	 * 
	 */
	private static final long serialVersionUID = 477694444629115020L;

	@Override
	public final gate.Resource init() throws ResourceInstantiationException {
		return this;
	}

	@Override
	public final void reInit() throws ResourceInstantiationException {
		init();
	}

	@Override
	public final void execute() throws ExecutionException {
		AnnotationSet set = document.getAnnotations();
		AnnotationSet univSet = set.get("Affiliation_univ");
		Annotation metadata = set.get("Metadata_body").iterator().next();
		AnnotationSet locSet = set.getContained(metadata.getStartNode().getOffset(), metadata.getEndNode().getOffset()).get("Location");
		boolean flag = false;
		List<Annotation> univs = gate.Utils.inDocumentOrder(univSet);
		for(Annotation univ : univs) {
			FeatureMap feats = univ.getFeatures();
			if(!feats.containsKey("locatedIn")) {
				flag = true;
				System.out.println("★ Hmmm... "  + univ.getFeatures().get("content").toString() + " does not have location information.");
			}
		}
		if(flag) {
				System.out.println("★ Running heuristics for " + document.getName());

				List<Annotation> locs = gate.Utils.inDocumentOrder(locSet);
				// only keep the countries
				System.out.println("\t★ Pruning the Location annotations...");
				for (Iterator<Annotation> iter = locs.listIterator(); iter.hasNext(); ) {
				    Annotation loc = iter.next();
				    if(loc.getFeatures().containsKey("locType")) {
				    	try{
				    		//FIXME
				    		//System.out.println("Is " + gate.Utils.cleanStringFor(document, loc) + " a country? " + loc.getFeatures().get("locType").toString().equalsIgnoreCase("country"));
				    		// use the line below to cause an exception
				    		loc.getFeatures().get("locType").toString().equalsIgnoreCase("country");
				    		if(loc.getFeatures().get("locType") != null && loc.getFeatures().get("locType").toString().trim().length() > 0 && !loc.getFeatures().get("locType").toString().equalsIgnoreCase("country")) {
								System.out.println("\t★★ Removing " + gate.Utils.cleanStringFor(document, loc));
						        iter.remove();
							}
				    	}catch (NullPointerException e){
				    		System.out.println("\t★★ Found NULL as locType. Removing " + gate.Utils.cleanStringFor(document, loc));
					        iter.remove();
				    	}					
					}else{
						//System.out.println("Skipping " + loc.getFeatures());
				        iter.remove();
					}

				}
				
				//System.out.println("Remaining locs " + locs);

				/*
				 * Heuristic 1: 
				 * No countries in the metadata body. Infer using DBpedia.
				 */
				if(locs.size() == 0) {
					System.out.println("\t★ Executing Heuristic [1] (DBpedia Lookup) for " + document.getName());
					// no countries, let's ask DBpedia
					for(Annotation univ : univs) {
						String uniName = univ.getFeatures().get("content").toString();
						//System.out.println("----- " + uniName);
						String uri = dbpediaLookup(uniName);
						if(uri != null) {
							findCountry(uri, univ);
						}else{
							System.out.println("\t★★ Sorry, no country information exists in DBpedia.");
						}
					}
				} else {
					/*
					 * Heuristic 2: 
					 * Infer the location based on text distance (closest).
					 */
					System.out.println("\t★ Executing Heuristic [2] (Shortest Distance) for " + document.getName());
					
					for(Annotation u : univs){
						Iterator<Annotation> itr = locs.iterator();
						while(itr.hasNext()){
							Long univOffset = u.getStartNode().getOffset();
							Annotation country = itr.next();
							Long countryOffset = country.getStartNode().getOffset();
							if(countryOffset < univOffset){
								// fault tolerance
								//System.out.println("Dangling country: " + gate.Utils.cleanStringFor(document, country));
								itr.remove();
								continue;
							}else{
								String locString = gate.Utils.cleanStringFor(document, country);
								u.getFeatures().put("locatedIn", locString);
								try {
									u.getFeatures().put("locationURI", new URI("http://ceur-ws.org/country/" + locString.trim().toLowerCase().replace(' ', '-')));
								} catch (URISyntaxException e) {
									e.printStackTrace();
								}
								itr.remove();
								System.out.println("\t★★ Inferred: (" + gate.Utils.cleanStringFor(document, u) + ", locatedIn, " + locString + ")");
								break;
							}
						}
					}

				}
		}
	}

	private String dbpediaLookup(final String university) {
		try {
			//System.out.println("----- dbpediaLookup " + university);
			final String endpointQuery = "http://lookup.dbpedia.org/api/search/KeywordSearch?QueryClass=University&MaxHits=1&QueryString=" + university;
			//System.out.println("----- query: " + endpointQuery);
			final URL url = new URL(endpointQuery);
			final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("content-type", "application/x-www-form-urlencoded");
			conn.setDoOutput(true);
			if(conn.getResponseCode() != 200) { throw new RuntimeException("Failed: Http error code " + conn.getResponseCode()); }
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), Charset.forName("UTF-8")));
			String s = null;
			StringBuffer sb = new StringBuffer();
			while((s = in.readLine()) != null) {
				sb.append(s);
			}
			//System.out.println(sb.toString());
			JsonParser parser = new JsonParser();
			JsonObject object = parser.parse(sb.toString()).getAsJsonObject();
			JsonArray arr = object.getAsJsonArray("results");
			// get(0) throws java.lang.IndexOutOfBoundsException: Index: 0, Size: 0 when nothing is found
			String uri = arr.get(0).getAsJsonObject().getAsJsonPrimitive("uri").getAsString();
			return uri;
		} catch(MalformedURLException e1) {
			e1.printStackTrace();
		} catch(IOException e2) {
			e2.printStackTrace();
		} catch(Exception e3){
			e3.printStackTrace();
		}
		return null;
	}

	private void findCountry(final String universityURI, Annotation univ) {
		//System.out.println("----- country for " + universityURI);

		String sparqlQueryString = " select (str(?label) as ?strLabel) " + "where {" + "<" + universityURI +
			"> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/University>;" +
			"<http://dbpedia.org/ontology/country> ?country." + "?country <http://www.w3.org/2000/01/rdf-schema#label> ?label." +
			"filter langMatches( lang(?label), 'en' )" + "}";
		//System.out.println(sparqlQueryString);
		Query query = QueryFactory.create(sparqlQueryString);
		QueryExecution qexec = QueryExecutionFactory.sparqlService("http://dbpedia.org/sparql", query);
		try {
			ResultSet results = qexec.execSelect();
			if(!results.hasNext()){
				System.out.println("\t★★ DBpedia returned no information for " + universityURI);
			}else{
				for(; results.hasNext();) {
					QuerySolution soln = results.nextSolution();
					System.out.println("\t★★ Infered: (" + universityURI + ", locatedIn, " + soln.get("?strLabel").toString().trim() + ")");
					univ.getFeatures().put("locatedIn", soln.get("?strLabel").toString().trim());
					univ.getFeatures().put("locationURI", new URI("http://ceur-ws.org/country/" + soln.get("?strLabel").toString().trim().toLowerCase().replace(' ', '-')));
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			qexec.close();
		}
	}
} // class LocationInferer
