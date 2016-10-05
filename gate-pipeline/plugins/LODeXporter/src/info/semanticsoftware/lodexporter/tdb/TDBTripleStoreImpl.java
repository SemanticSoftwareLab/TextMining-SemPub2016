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

package info.semanticsoftware.lodexporter.tdb;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.commons.lang.NullArgumentException;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.vocabulary.RDF;

import gate.FeatureMap;
import info.semanticsoftware.lodexporter.PropertyMapping;
import info.semanticsoftware.lodexporter.RelationMapping;
import info.semanticsoftware.lodexporter.SubjectMapping;
import info.semanticsoftware.lodexporter.TripleStoreInterface;

import org.apache.log4j.Logger;

/**
 * Concrete implementation for interacting with a TDB-based triple store.
 * 
 * @author Bahar Sateli
 * @author Rene Witte
 * 
 */
public class TDBTripleStoreImpl implements TripleStoreInterface {

    private Dataset dataset;
    private Model model;
    private HashMap<String, Property> propertyModelHash;
    private HashMap<String, Property> relationModelHash;
    // FIXME why using a diff uri?
    private final String modelBaseURI = "http://lod.semanticsoftware.info/pubo/pubo#";
    private Property hasAnnotation;
    private Property hasDocument;
    private Property hasCompetencyRecord;
    private Property competenceFor;

    protected static final Logger LOGGER = Logger.getLogger(TDBTripleStoreImpl.class);

    public TDBTripleStoreImpl() {
        // This constructor is intentionally empty. Nothing special is needed
        // here.
    }

    /*
     * (non-Javadoc)
     * 
     * @see info.semanticsoftware.lodexporter.TripleStoreInterface#connect()
     */
    public void connect(final String dir) {
        dataset = TDBFactory.createDataset(dir);
        LOGGER.debug("[connect] Dataset is now: " + dataset);
    }

    /*
     * (non-Javadoc)
     * 
     * @see info.semanticsoftware.lodexporter.TripleStoreInterface#disconnect()
     */
    public void disconnect() {
        dataset.close();
        TDBFactory.reset();
        LOGGER.debug("[disconnect] Dataset is now: " + dataset);
    }

    @Override
    public void beginTransaction(final TransactionType type) {
        if (type == TransactionType.READ) {
            dataset.begin(ReadWrite.READ);
        } else {
            dataset.begin(ReadWrite.WRITE);
        }
    }

    @Override
    public void endTransaction() {
        dataset.commit(); // commit the transaction, otherwise it would be
                          // aborted when calling end()
        dataset.end();
    }

    /**
     * Generates a map of &lt;rulename,SubjectMapping&gt; objects from the query
     * results.
     * 
     * @param query
     *            the SPARQL query
     * @return a map of &lt;rulename,SubjectMapping&gt; objects
     * @throws Exception
     *             from the TDB implementation
     * 
     * @see info.semanticsoftware.lodexporter.LODexporter#init()
     * 
     */
    @Override
    public Map<String, SubjectMapping> getSubjectMappings(final String query) throws Exception {
        Map<String, SubjectMapping> subMapList = null;
        final ResultSet rs = queryMappings(query);
        subMapList = populateSubjectHash(rs);

        return subMapList;
    }

    @Override
    public HashMap<String, LinkedList<PropertyMapping>> getPropertyMappings(final String query)
            throws Exception {
        HashMap<String, LinkedList<PropertyMapping>> propMapList = null;
        final ResultSet rs = queryMappings(query);
        propMapList = populatePropertyMapList(rs);
        prepareTDBPropertyModel(propMapList);

        return propMapList;
    }

    @Override
    public HashMap<String, LinkedList<RelationMapping>> getRelationMappings(final String query)
            throws Exception {
        HashMap<String, LinkedList<RelationMapping>> relationMapList = null;
        final ResultSet rs = queryMappings(query);
        relationMapList = populateRelationMapList(rs);
        prepareTDBRelationModel(relationMapList);

        return relationMapList;
    }

    /*
     * public static synchronized TDBTripleStoreImpl getInstance() { if
     * (instance == null) { instance = new TDBTripleStoreImpl(); } return
     * instance; }
     */

    private ResultSet queryMappings(final String query) throws Exception {
        QueryExecution qExec = QueryExecutionFactory.create(query, dataset);
        final ResultSet rs = qExec.execSelect();

        return rs;
    }

    private Map<String, SubjectMapping> populateSubjectHash(final ResultSet rs) {
        final Map<String, SubjectMapping> subjectHash = new HashMap<String, SubjectMapping>();

        try {
            /*
             * Iterate through the SPARQL query results and creates a new
             * SubjectMapping object. Each result is supposed to contain: -
             * ?rule rule name - ?baseURI base URI - ?GATEtype GATE annotation
             * type - ?type rdf:type value
             */
            while (rs.hasNext()) {

                // TODO issue a warning/exception when the rule is incomplete?
                final QuerySolution soln = rs.nextSolution();
                final RDFNode ruleNode = soln.get("?rule");
                String ruleString = null;
                if (ruleNode != null)
                    ruleString = ruleNode.asResource().getURI();

                final RDFNode baseURINode = soln.get("?baseURI");
                String baseURIString = null;
                if (baseURINode != null)
                    baseURIString = baseURINode.asResource().getURI();

                final RDFNode GATETypeNode = soln.get("?GATEtype");
                String GATETypeString = null;
                if (GATETypeNode != null)
                    GATETypeString = GATETypeNode.asLiteral().getString();

                final RDFNode typeNode = soln.get("?type");
                String typeString = null;
                if (typeNode != null)
                    typeString = typeNode.asResource().getURI();

                final SubjectMapping newMap = new SubjectMapping(ruleString, baseURIString,
                        typeString, GATETypeString); // NOPMD
                subjectHash.put(ruleString, newMap);
            }
        } catch (Exception e) {
            LOGGER.error("Error reading the subject mappings.", e);
        }

        LOGGER.debug("----- SUBJECT MAPLIST: " + subjectHash);
        return subjectHash;

    }

    private HashMap<String, LinkedList<PropertyMapping>> populatePropertyMapList(final ResultSet rs) {
        final HashMap<String, LinkedList<PropertyMapping>> propertyHash = new HashMap<String, LinkedList<PropertyMapping>>();

        try {
            while (rs.hasNext()) {
                final QuerySolution soln = rs.nextSolution();

                final RDFNode ruleNode = soln.get("?rule");
                String ruleString = null;
                if (ruleNode != null)
                    ruleString = ruleNode.asResource().getURI();

                final RDFNode GATEtypeNode = soln.get("?GATEtype");
                String GATEtypeString = null;
                if (GATEtypeNode != null)
                    GATEtypeString = GATEtypeNode.asLiteral().getString();

                /*
                 * RDFNode baseURINode = soln.get("?baseURI"); String
                 * baseURIString = null; if (baseURINode != null) baseURIString
                 * = baseURINode.asResource().getURI();
                 */

                final RDFNode GATEfeatureNode = soln.get("?GATEfeature");
                String GATEfeatureString = null;
                if (GATEfeatureNode != null)
                    GATEfeatureString = GATEfeatureNode.asLiteral().getString();

                final RDFNode GATEattributeNode = soln.get("?GATEattribute");
                String GATEattributeString = null;
                if (GATEattributeNode != null)
                    GATEattributeString = GATEattributeNode.asLiteral().getString();

                final RDFNode typeNode = soln.get("?type");
                String typeString = null;
                if (typeNode != null)
                    typeString = model.expandPrefix(typeNode.asResource().getURI());

                final PropertyMapping newMap = new PropertyMapping(ruleString, typeString,
                        GATEtypeString, GATEfeatureString, GATEattributeString); // NOPMD

                if (propertyHash.containsKey(ruleString)) {
                    propertyHash.get(ruleString).add(newMap);
                } else {
                    final LinkedList<PropertyMapping> propertyMaps = new LinkedList<>();
                    propertyMaps.add(newMap);
                    propertyHash.put(ruleString, propertyMaps);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error populating the property hashmap.", e);
        }

        LOGGER.debug("----- PROPERTY HASHMAP:" + propertyHash);
        return propertyHash;

    }

    private HashMap<String, LinkedList<RelationMapping>> populateRelationMapList(final ResultSet rs) {
        final HashMap<String, LinkedList<RelationMapping>> relationHash = new HashMap<String, LinkedList<RelationMapping>>();

        try {
            while (rs.hasNext()) {
                final QuerySolution soln = rs.nextSolution();

                final RDFNode ruleNode = soln.get("?rule");
                String ruleString = null;
                if (ruleNode != null)
                    ruleString = ruleNode.asResource().getURI();
                // System.out.println("Rule:" + ruleNode + ", localName=" +
                // ruleNode.asResource().getLocalName() + ", nameSpace=" +
                // ruleNode.asResource().getNameSpace());

                final RDFNode domainNode = soln.get("?domain");
                String domainString = null;
                if (domainNode != null) {
                    domainString = domainNode.asResource().getURI();
                } else {
                    throw new NullArgumentException("Missing domain for rule: " + ruleString);
                }

                final RDFNode rangeNode = soln.get("?range");
                String rangeString = null;
                if (rangeNode != null) {
                    rangeString = rangeNode.asResource().getURI();
                } else {
                    throw new NullArgumentException("Missing range for rule: " + ruleString);
                }

                final RDFNode typeNode = soln.get("?type");
                String typeString = null;
                if (typeNode != null)
                    typeString = model.expandPrefix(typeNode.asResource().getURI());

                final RDFNode GATEattributeNode = soln.get("?GATEattribute");
                String GATEattributeString = null;
                if (GATEattributeNode != null)
                    GATEattributeString = GATEattributeNode.asLiteral().getString();

                final RelationMapping newMap = new RelationMapping(ruleString, typeString,
                        domainString, rangeString, GATEattributeString); // NOPMD

                if (relationHash.containsKey(domainString)) {
                    relationHash.get(domainString).add(newMap);
                } else {
                    LinkedList<RelationMapping> relationMaps = new LinkedList<>();
                    relationMaps.add(newMap);
                    relationHash.put(domainString, relationMaps);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error populating the relation hashmap.", e);
        }

        LOGGER.debug("----- RELATION HASHMAP:" + relationHash);
        return relationHash;

    }

    @Override
    public void initModel() {
        model = dataset.getDefaultModel();
    }

    private void prepareTDBPropertyModel(
            final HashMap<String, LinkedList<PropertyMapping>> propMapList) {
        // TODO define relations in the RDF rather than hard-coding it here
        hasAnnotation = model.createProperty(modelBaseURI, "hasAnnotation");
        hasDocument = model.createProperty(modelBaseURI, "hasDocument");

        // properties for relation annotations
        hasCompetencyRecord = model.createProperty("http://intelleo.eu/ontologies/user-model/ns/",
                "hasCompetencyRecord");
        competenceFor = model.createProperty("http://www.intelleo.eu/ontologies/competences/ns/",
                "competenceFor");

        propertyModelHash = new HashMap<>();
        for (final LinkedList<PropertyMapping> propMapElement : propMapList.values()) {
            for (final PropertyMapping map : propMapElement) {
                final String propKey = (map.getGATEattribute() == null) ? map.getGATEfeature()
                        : map.getGATEattribute();
                /*
                 * if(map.getGATEattribute() == null){ propKey =
                 * map.getGATEfeature(); }else{ propKey =
                 * map.getGATEattribute(); }
                 */
                propertyModelHash.put(propKey, model.createProperty(map.getType()));

            }
        }

    }

    private void prepareTDBRelationModel(
            final HashMap<String, LinkedList<RelationMapping>> relationMapList) {
        model = dataset.getDefaultModel();
        relationModelHash = new HashMap<>();
        for (final LinkedList<RelationMapping> relationMapElement : relationMapList.values()) {
            for (final RelationMapping rMap : relationMapElement) {
                relationModelHash.put(rMap.getRule(), model.createProperty(rMap.getType()));
            }
        }
    }

    @Override
    public void storeTriple(final String docURL, final String URIforAnnotation, final String type,
            final HashMap<String, Object> exportProps,
            final HashMap<String, LinkedList<PropertyMapping>> propertyMapList) {
        // System.out.println(docURL + ", " + URIforAnnotation + ", " + type);

        try {
            model = dataset.getDefaultModel();

            final Resource newTriple = model.createResource(model.expandPrefix(URIforAnnotation));

            for (final String propKey : exportProps.keySet()) {
                if (exportProps.get(propKey).getClass() == java.net.URI.class) {
                    // System.out.println("URI feature found: " +
                    // exportProps.get(propKey));
                    newTriple.addProperty(propertyModelHash.get(propKey),
                            model.createResource(exportProps.get(propKey).toString()));
                } else if ("URI".equals(propKey) || "URI1".equals(propKey)) { //FIXME remove the URI-n hack
                    // TODO remove this if, instead update the previous
                    // pipelines JAPE rules
                    newTriple.addProperty(propertyModelHash.get(propKey),
                            model.createResource((String) exportProps.get(propKey)));
                } else {
                    final Literal propValueLit = model.createTypedLiteral(exportProps.get(propKey));
                    newTriple.addProperty(propertyModelHash.get(propKey), propValueLit);
                }
            }

            newTriple.addProperty(RDF.type, model.createResource(model.expandPrefix(type)));
            model.createResource(docURL).addProperty(hasAnnotation, newTriple);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(docURL + ", " + URIforAnnotation + ", " + type);
            System.err.println(propertyMapList);
        }
    }

    @Override
    public void storeTriple(final String docURL, final RelationMapping rMap, final String URIforAnnotation,
            final String rangeURI) {
        try {
            model = dataset.getDefaultModel();
            final Resource newTriple = model.createResource(URIforAnnotation);

            newTriple.addProperty(relationModelHash.get(rMap.getRule()),
                    model.createResource(rangeURI));

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(docURL + ", " + URIforAnnotation + ", " + rangeURI);
        }

    }

    // method for storing relation annotations
    @Override
    public void storeTriple(final String docURL, final String annotationURI, final FeatureMap feats,
            final String domainURI, final String rangeURI) {
        try {
            model = dataset.getDefaultModel();

            final Resource relationNode = model.createResource(annotationURI);
            relationNode.addProperty(RDF.type, model.createResource((String) feats.get("type")));
            relationNode.addProperty(competenceFor, model.createResource(rangeURI));

            model.createResource(domainURI).addProperty(hasCompetencyRecord, relationNode);
            model.createResource(docURL).addProperty(hasAnnotation, relationNode);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void storeTriple(final String docURL, final String corpusURI) {
        try {
            model = dataset.getDefaultModel();
            model.createResource(corpusURI).addProperty(hasDocument, model.createResource(docURL));
            System.out.println("Exported " + corpusURI + " hasDocument " + docURL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public final String printDataset() {
        return dataset.toString();
    }
}
