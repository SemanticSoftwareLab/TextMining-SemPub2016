/*
 * LODeXporter -- http://www.semanticsoftware.info/lodexporter
 *
 * This file is part of the LODeXporter component.
 *
 * Copyright (c) 2015, Semantic Software Lab, http://www.semanticsoftware.info
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
 * 
 */

package info.semanticsoftware.lodexporter;

import info.semanticsoftware.lodexporter.TripleStoreInterface.TransactionType;
import info.semanticsoftware.lodexporter.tdb.TDBTripleStoreImpl;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Controller;
import gate.FeatureMap;
import gate.ProcessingResource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ControllerAwarePR;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.RunTime;
import gate.relations.Relation;
import gate.relations.RelationSet;

import org.apache.log4j.Logger;

/**
 * This class is the implementation of the resource LODEXPORTER.
 */
@CreoleResource(name = "LODexporter", comment = "An ontology-aware PR to transform GATE annotations to LOD triples.")
public class LODexporter extends AbstractLanguageAnalyser implements ProcessingResource,
        ControllerAwarePR {
    private static final long serialVersionUID = 1L;

    /**
     * A HashMap where keys are rule names, and values are SubjectMapping
     * objects. It is used to map each unique rule name to its corresponding
     * baseURI, GATEtype and rdf:type for export. Example: <map:GATEAnnotation1,
     * <rule= map:GATEAnnotation1,
     * baseURI=http://semanticsoftware.info/lodexporter/, GATEType= Person,
     * type= foaf:Person>>
     * info.semanticsoftware.lodexporter#getSubjectMappings() for populating the
     * map.
     */
    private Map<String, SubjectMapping> subjectHash;

    /**  */
    private HashMap<String, LinkedList<PropertyMapping>> propertyMapList;

    /**  */
    private HashMap<String, LinkedList<RelationMapping>> relationMapList;
    private TripleStoreInterface myTripleStore;
    private String pipelineName;
    private String corpusName;
    private String sessionID;
    protected static final Logger LOGGER = Logger.getLogger(LODexporter.class);

    // creole parameters
    @CreoleParameter(comment = "RDF store directory", defaultValue = "/tmp/tdb")
    private String rdfStoreDir;

    @CreoleParameter(comment = "Mapping SPARQL query", defaultValue = "SELECT ?rule ?type ?baseURI ?GATEtype "
            + "WHERE { "
            + "?rule ?p <map:Mapping> . "
            + "?rule <http://lod.semanticsoftware.info/mapping/mapping#type> ?type . "
            + "?rule <http://lod.semanticsoftware.info/mapping/mapping#baseURI> ?baseURI . "
            + "?rule <http://lod.semanticsoftware.info/mapping/mapping#GATEtype> ?GATEtype .} ")
    private String subjectMappingSparql;

    @CreoleParameter(comment = "PropertyMapping SPARQL query", defaultValue = "SELECT ?rule ?GATEtype ?GATEattribute ?GATEfeature ?type "
            + "WHERE { "
            + "?rule ?p <map:Mapping> . "
            + "?rule <http://lod.semanticsoftware.info/mapping/mapping#GATEtype> ?GATEtype."
            + "?rule <http://lod.semanticsoftware.info/mapping/mapping#hasMapping> ?mapping ."
            + "?mapping <http://lod.semanticsoftware.info/mapping/mapping#type> ?type . "
            + "OPTIONAL {?mapping <http://lod.semanticsoftware.info/mapping/mapping#GATEattribute> ?GATEattribute . }"
            + "OPTIONAL {?mapping <http://lod.semanticsoftware.info/mapping/mapping#GATEfeature> ?GATEfeature . }}")
    private String propertyMappingSparql;

    @CreoleParameter(comment = "RelationMapping SPARQL query", defaultValue = "SELECT ?rule ?type ?domain ?range ?GATEattribute "
            + "WHERE { "
            + "?rule ?p <map:Mapping> . "
            + "?rule <http://lod.semanticsoftware.info/mapping/mapping#type> ?type . "
            + "?rule <http://lod.semanticsoftware.info/mapping/mapping#domain> ?domain . "
            + "?rule <http://lod.semanticsoftware.info/mapping/mapping#range> ?range . "
            + "OPTIONAL {?rule <http://lod.semanticsoftware.info/mapping/mapping#GATEattribute> ?GATEattribute . }}")
    private String relationMappingSparql;

    @CreoleParameter(comment = "The annotation set to use as input", defaultValue = "")
    @RunTime
    private String inputASName;

    @CreoleParameter(comment = "Use custom URIs", defaultValue = "false")
    @RunTime
    private Boolean customURI;

	/**
     * Sets whether custom URI generation style should be used.
     * 
     * @param myUriStyle
     *            the URI generation style (e.g., default or custom)
     */

    public void setCustomURI(Boolean myUriStyle) {
		this.customURI = myUriStyle;
	}

    /**
     * @return the uriStyle
     */    
    public Boolean getCustomURI() {
		return customURI;
	}

	/**
     * Sets the path to the triple store directory.
     * 
     * @param myRdfStoreDir
     *            the path to the triple store directory
     */
    public final void setrdfStoreDir(final String myRdfStoreDir) {
        this.rdfStoreDir = myRdfStoreDir;
    }

    /**
     * @return the rdfstoredir
     */
    public final String getrdfStoreDir() {
        return rdfStoreDir;
    }

    /**
     * @return the subjectMappingSparql
     */
    public final String getSubjectMappingSparql() {
        return subjectMappingSparql;
    }

    /**
     * @param mySubjectMappingSparql
     *            the subjectMappingSparql to set
     */
    public final void setSubjectMappingSparql(final String mySubjectMappingSparql) {
        this.subjectMappingSparql = mySubjectMappingSparql;
    }

    /**
     * @return the propertyMappingSparql
     */
    public final String getPropertyMappingSparql() {
        return propertyMappingSparql;
    }

    /**
     * @param myPropertyMappingSparql
     *            the propertyMappingSparql to set
     */
    public final void setPropertyMappingSparql(final String myPropertyMappingSparql) {
        this.propertyMappingSparql = myPropertyMappingSparql;
    }

    /**
     * @return the relationMappingSparql
     */
    public final String getRelationMappingSparql() {
        return relationMappingSparql;
    }

    /**
     * @param myRelationMappingSparql
     *            the relationMappingSparql to set
     */
    public final void setRelationMappingSparql(final String myRelationMappingSparql) {
        this.relationMappingSparql = myRelationMappingSparql;
    }

    public final void setInputASName(final String myInputASName) {
        this.inputASName = myInputASName;
    }

    public final String getInputASName() {
        return this.inputASName;
    }

    @Override
    public final gate.Resource init() throws ResourceInstantiationException {
        LOGGER.debug("LODeXporter loaded!");
        // myTripleStore = TDBTripleStoreImpl.getInstance();
        myTripleStore = new TDBTripleStoreImpl();
        myTripleStore.connect(getrdfStoreDir());
        try {
            myTripleStore.beginTransaction(TransactionType.WRITE);
            myTripleStore.initModel();
            subjectHash = myTripleStore.getSubjectMappings(getSubjectMappingSparql());
            propertyMapList = myTripleStore.getPropertyMappings(getPropertyMappingSparql());
            relationMapList = myTripleStore.getRelationMappings(getRelationMappingSparql());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            myTripleStore.endTransaction();
        }

        // testTDB();
        return this;
    }

    @Override
    public final void reInit() throws ResourceInstantiationException {
        myTripleStore.disconnect();
        init();
    }

    @Override
    public final void execute() throws ExecutionException {
        sessionID = UUID.randomUUID().toString();
        String docURL = "";
        String corpusURI = "";
        // find out whether we should use custom URIs for corpus and documents
        if(getCustomURI()){
        	LOGGER.info("generating custom URIs");
        	try{
        	corpusURI = java.net.URLDecoder.decode(corpusName, "UTF-8");
        	String docName = document.getName();
    			int index = docName.indexOf(".");
    			if(index > -1){
    				docName = docName.substring(0, index);
    			}
    			docURL = corpusURI + "#" + docName;
    			docURL = java.net.URLDecoder.decode(docURL, "UTF-8");
    		}
    		catch(Exception e){
    			e.printStackTrace();
    		}
        }else{
        	docURL = (String) document.getFeatures().get("gate.SourceURL");
        	docURL = fixProtocol(docURL);
        	corpusURI = "http://semanticsoftware.info/lodexporter/Corpus/" + corpusName;
        }
        
        /*
         * System.out.println("Mapping inside execute:"); for (SubjectMapping m
         * : subjectMapList) { System.out.println(m.toString()); }
         */

        try {
            // one transaction per document
            myTripleStore.beginTransaction(TransactionType.WRITE);
            // first, export the document-corpus relation triple
            myTripleStore.storeTriple(docURL, corpusURI);

            final AnnotationSet inputAS = inputASName == null || inputASName.trim().length() == 0 ? document.getAnnotations()
                    : document.getAnnotations(inputASName); //NOPMD

            final HashMap<String, Object> exportPropertyMap = new HashMap<String, Object>();
            final HashMap<String, Object> exportRelationMap = new HashMap<String, Object>();
            
            for (final SubjectMapping aMapping : subjectHash.values()) {
                final AnnotationSet annotSet = inputAS.get(aMapping.getGATEType());
                final String currentRule = aMapping.getRule();
                final List<PropertyMapping> propsForType = propertyMapList.get(currentRule);
                final List<RelationMapping> relationsForType = relationMapList.get(currentRule);

                LOGGER.debug("Mapping " + aMapping.getGATEType() + " with props: " + propsForType
                        + " and relations " + relationsForType + " for rule: " + currentRule);

                for (final Annotation currAnnot : annotSet) {
                    exportPropertyMap.clear();
                    exportRelationMap.clear();

                    final FeatureMap feats = currAnnot.getFeatures();
                    processProperties(docURL, propsForType, currAnnot, exportPropertyMap, feats);

                    myTripleStore.storeTriple(
                            docURL,
                            getURIforAnnotation(currAnnot, aMapping.getBaseURI(), sessionID,
                                    currentRule), aMapping.getType(), exportPropertyMap,
                            propertyMapList);

                    processRelations(docURL, relationsForType, currAnnot, exportRelationMap,
                            aMapping, sessionID);
                }
            }

            // the second empty string means the relations are in the default
            // annotation set
            processRelationsAdHoc(docURL, "");

        } catch (Exception e) {
            LOGGER.error("Error in processing document " + document.getName(), e);
        } finally {
            myTripleStore.endTransaction();
        }

    }

    private void processRelationsAdHoc(final String docURL, final String annotationSetName) {
        final RelationSet relationSet = document.getAnnotations(annotationSetName).getRelations();
        String domainURI = null;
        String rangeURI = null;

        for (final Relation relation : relationSet) {
            final String relationURI = getURIforRelation(relation,
                    "http://semanticsoftware.info/lodexporter/", relation.getType());
            // System.out.println("Storing rel: " + relationURI);

            final int[] members = relation.getMembers();
            if (members.length == 2) {
                final Annotation domainAnnot = document.getAnnotations().get(members[0]);
                final Annotation rangeAnnot = document.getAnnotations().get(members[1]);

                for (final SubjectMapping aMapping : subjectHash.values()) {
                    if (aMapping.getGATEType().equals(domainAnnot.getType())) {
                        domainURI = getURIforAnnotation(domainAnnot, aMapping.getBaseURI(),
                                sessionID, aMapping.getRule());
                    } else if (aMapping.getGATEType().equals(rangeAnnot.getType())) {
                        rangeURI = getURIforAnnotation(rangeAnnot, aMapping.getBaseURI(),
                                sessionID, aMapping.getRule());
                    }
                }

                myTripleStore.storeTriple(docURL, relationURI, relation.getFeatures(), domainURI,
                        rangeURI);

            } else {
                System.out.println("This relation does not have two members. Skipping relation #"
                        + relation.getId());
            }
        }

    }

    // FIXME merge with the other with a superclass of annotation and relation?
    private String getURIforRelation(final Relation relation, final String baseURI,
            final String rule) {
        final String reID = relation.getId().toString();
        return baseURI + sessionID + "/" + relation.getType() + "/" + reID + "#" + rule;
    }

    //FIXME look into why exportRelationMap is passed but not used?
    private void processRelations(final String docURL, final List<RelationMapping> relationsForType,
            final Annotation currAnnot, final HashMap<String, Object> exportRelationMap,
            final SubjectMapping currentSubjMapping, final String sessionID) {
        if (relationsForType != null) {
            for (final RelationMapping rMap : relationsForType) {
                if (rMap.getGATEattribute() != null && rMap.getGATEattribute().equals("contains")) {
                    final SubjectMapping rangeMapping = subjectHash.get(rMap.getRange());
                    final String rangeGATEType = rangeMapping.getGATEType();
                    final String rangeBaseURI = rangeMapping.getBaseURI();
                    final String rangeRuleName = rangeMapping.getRule();

                    // System.out.println("----------------------- range " +
                    // rMap.getRange() + " - type: " + rangeGATEType +
                    // " baseuri: " + rangeBaseURI + " rule: " + rangeRuleName);

                    final AnnotationSet containedEntities = document.getAnnotations().getContained(
                            currAnnot.getStartNode().getOffset(),
                            currAnnot.getEndNode().getOffset()).get(rangeGATEType);

                    for (final Annotation aNE : containedEntities) {
                        final String rangeURI = getURIforAnnotation(aNE, rangeBaseURI, sessionID,
                                rangeRuleName);
                        myTripleStore.storeTriple(
                                docURL,
                                rMap,
                                getURIforAnnotation(currAnnot, currentSubjMapping.getBaseURI(),
                                        sessionID, rMap.getDomain()), rangeURI);
                    }
                }else if(rMap.getGATEattribute() != null && rMap.getGATEattribute().equals("employedBy")){
					SubjectMapping rangeMapping = subjectHash.get(rMap.getRange());
					String rangeBaseURI = rangeMapping.getBaseURI();
					String rangeRuleName = rangeMapping.getRule();										
					
					Integer affiliationID = (Integer) currAnnot.getFeatures().get("employedBy");
					Annotation affiliationAnnot = document.getAnnotations().get(affiliationID);
					String rangeURI = getURIforAnnotation(affiliationAnnot, rangeBaseURI , sessionID, rangeRuleName);
					myTripleStore.storeTriple(docURL, rMap, getURIforAnnotation(currAnnot, currentSubjMapping.getBaseURI(), sessionID, rMap.getDomain()), rangeURI);
					
				}else {
                    // we have the URI of the domain (i.e., the subject), we
                    // only need to find the URI of the range (i.e., the object)
                    final String rangeURI = getURIforAnnotation(currAnnot,
                            currentSubjMapping.getBaseURI(), sessionID, rMap.getRange());
                    myTripleStore.storeTriple(
                            docURL,
                            rMap,
                            getURIforAnnotation(currAnnot, currentSubjMapping.getBaseURI(),
                                    sessionID, rMap.getDomain()), rangeURI);
                }

            }
        }
    }

    private void processProperties(final String docURL, final List<PropertyMapping> propsForType,
            final Annotation currAnnot, final HashMap<String, Object> exportPropertyMap, final FeatureMap feats)
            throws ExecutionException {
        if (propsForType != null) {
            for (final PropertyMapping pMap : propsForType) {
                if (pMap.getGATEfeature() != null && pMap.getGATEattribute() != null) {
                    throw new ExecutionException("Both GATE feature and attributes have values.");
                } else if (pMap.getGATEfeature() != null) {
                    // export the property only if the feature key exists
                    if (feats.containsKey(pMap.getGATEfeature())) {
                        // export the property only if the declared feature has
                        // a value (i.e., not null)
                        final Object featValue = feats.get(pMap.getGATEfeature());
                        if (featValue != null) {
                            exportPropertyMap.put(pMap.getGATEfeature(), featValue);
                        } else {
                            System.err.println("WARNING: " + pMap.getGATEfeature()
                                    + " has a NULL value in document (" + docURL
                                    + ") for annotation #" + currAnnot.getId()
                                    + ". I'm going to skip exporting this feature.");
                        }
                    }

                } else if (pMap.getGATEattribute() != null) {
                    exportPropertyMap.put(pMap.getGATEattribute(),
                            getValueforGATEAttribute(pMap.getGATEattribute(), currAnnot));
                } else {
                    throw new ExecutionException("Both GATE feature and attributes are null.");
                }
            }
        }
    }

    private Object getValueforGATEAttribute(final String gateAttribute, final Annotation currAnnot) {
        Object value = null;
        try {
            switch (gateAttribute) {
            case "content":
                value = document.getContent().getContent(currAnnot.getStartNode().getOffset(),
                        currAnnot.getEndNode().getOffset()).toString();
                break;
            case "startOffset":
                value = currAnnot.getStartNode().getOffset();
                break;
            case "endOffset":
                value = currAnnot.getEndNode().getOffset();
                break;
            case "docURL":
                value = new URI((String) document.getFeatures().get("gate.SourceURL"));
                break;
            case "annotatedAt":
                // TODO keep the time zone in a separate properties file
                value = new XSDDateTime(
                        Calendar.getInstance(TimeZone.getTimeZone("America/Montreal")));
                break;
            case "annotatedBy":
                value = pipelineName;
                break;
            default:
                throw new IllegalArgumentException("Unsuppport GATE attribute: " + gateAttribute);
            }
        } catch (IllegalArgumentException e){
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return value;
    }

    private String getURIforAnnotation(final Annotation re, final String baseURI, final String sessionID,
            final String ruleName) {
        final String reID = re.getId().toString();
        return baseURI + sessionID + "/" + re.getType() + "/" + reID + "#" + ruleName;

    }

    private String fixProtocol(final String docURL) {
        return docURL.replaceFirst("file:\\/", "http://");
    }

    @Override
    public final void controllerExecutionAborted(final Controller arg0, final Throwable arg1)
            throws ExecutionException {
        // release the connection to all datasets to allow other applications
        // use the tdb
        // myTripleStore.disconnect();
    }

    @Override
    public final void controllerExecutionFinished(final Controller arg0) throws ExecutionException {
        // release the connection to all datasets to allow other applications
        // use the tdb
        // myTripleStore.disconnect();

        LOGGER.debug("[controllerExecutionFinished] Dataset is now: " + myTripleStore.printDataset());
    }

    @Override
    public final void controllerExecutionStarted(final Controller controller) throws ExecutionException {
        // System.out.println("Hello controller: " + arg0.getName());
        pipelineName = controller.getName();
        try {
            corpusName = URLEncoder.encode(corpus.getName(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        LOGGER.debug("[controllerExecutionStarted] Dataset is now: " + myTripleStore.printDataset());
    }

} // class LODexporter
