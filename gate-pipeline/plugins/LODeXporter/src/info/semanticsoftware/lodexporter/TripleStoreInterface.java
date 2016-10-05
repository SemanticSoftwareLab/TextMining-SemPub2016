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

import gate.FeatureMap;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author Bahar Sateli
 * @author Rene Witte
 */
public interface TripleStoreInterface {
	enum TransactionType{READ, WRITE}; 

	void connect( String dir );
	void disconnect();
	void initModel();
	
	/**
	 * Queries the triple store for mapping rules using the supplied SPARQL query string 
	 * and populates a map of &lt;rulename, {@link SubjectMapping}&gt; objects.
	 * @param query the SPARQL query string
	 * @return A HashMap of &lt;rulename, {@link SubjectMapping}&gt; objects
	 * @throws Exception from the underlying triple store implementation
	 */
	Map<String, SubjectMapping> getSubjectMappings( String query ) throws Exception;
	HashMap<String,LinkedList<PropertyMapping>> getPropertyMappings( String query )throws Exception;
	HashMap<String,LinkedList<RelationMapping>> getRelationMappings( String query )throws Exception;
	void beginTransaction(TransactionType type);
	void endTransaction();
	void storeTriple(String docURL, String URIforAnnotation,
			String type, HashMap<String, Object> exportProps, HashMap<String, LinkedList<PropertyMapping>> propertyMapList);
	void storeTriple(String docURL, RelationMapping rMap, String URIforAnnotation, String rangeURI);
	void storeTriple(String docURL, String corpusURI);
	void storeTriple(String docURL, String annotationURI, FeatureMap feats, String domainURI, String rangeURI);
	String printDataset();
}
