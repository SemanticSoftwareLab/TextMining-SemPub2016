/*
 * Semantic Software Lab submission to Semantic Publishing Challenge 2016,
 * http://www.semanticsoftware.info/sempub-challenge-2015
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import gate.*;
import gate.creole.*;
import gate.creole.metadata.*;
import gate.util.*;


/** 
 * This class is the implementation of the resource AUTHORAFFILIATIONINFERER.
 */
@CreoleResource(name = "AuthorAffiliationInferer",
        comment = "Add a descriptive comment about this resource")
public class AuthorAffiliationInferer  extends AbstractLanguageAnalyser
  implements ProcessingResource {
      /**
       *
       */
      private static final long serialVersionUID = 1L;
      
      @Override
      public void execute() throws ExecutionException {
          System.out.println("Analyzing relations between authors and affiliations for " + document.getName() + "...");
          AnnotationSet annotSet = document.getAnnotations();
          AnnotationSet authors = annotSet.get("Author");
          AnnotationSet affiliations = annotSet.get("Affiliation_univ");
          
          //get the annotations in a list
          List<Annotation> authorList = new ArrayList<Annotation>(authors);
          List<Annotation> affiliationList = new ArrayList<Annotation>(affiliations);
          
          //sort the list by offset
          Collections.sort(authorList, new OffsetComparator());
          Collections.sort(affiliationList, new OffsetComparator());
          
          //HEURISTIC 0: if authors and affiliations are indexed, we simply match them using their indices
          if(affiliationList.size() != 0 && authorList.size() != 0){
              
              //FIXME what if the 0th item doesn't have an index?
              if(authorList.get(0).getFeatures().get("index") != null && affiliationList.get(0).getFeatures().get("index") != null){
                  //System.out.println("DEBUG: authors and affiliations are indexed for doc " + document.getName());
                  try{
                      for(Annotation author: authorList){
                          FeatureMap feats = author.getFeatures();
                          System.out.println("checking author " + author.getId());
                          if(feats.get("index") != null){
                              int iAuthor = Integer.parseInt((String) feats.get("index"));
                              for(Annotation affiliation:affiliationList){
                                  System.out.println("checking affiliation " + affiliation.getId());
                                  if(affiliation.getFeatures().get("index") != null){
                                      int iAffiliation = Integer.parseInt((String) affiliation.getFeatures().get("index"));
                                      if(iAuthor == iAffiliation){
                                          feats.put("employedBy", affiliation.getId());
                                          //System.out.println("updated: " + feats);
                                          break;
                                      }
                                  }
                                  
                              }
                          }
                      }
                  }catch(Exception e){
                      System.out.println("Found an annotation that does not have an index feature. Skipping...");
                      e.printStackTrace();
                  }
              }else if(affiliationList.size() == 1){
                  //HEURISTIC 1: Multiple authors, One affiliation
                  //System.out.println("DEBUG: Multiple authors, One affiliation for doc " + document.getName());
                  for(int i = 0; i < authorList.size(); i++){
                      Annotation author = (Annotation)authorList.get(i);
                      FeatureMap feats = author.getFeatures();
                      feats.put("employedBy", affiliationList.get(0).getId());
                      //System.out.println("updated: " + feats);
                  }
              }else if(affiliationList.size() == authorList.size()){
                  //HEURISTIC 2: Same number of authors and affiliations
                  //System.out.println("DEBUG: Same number of authors and affiliations for doc " + document.getName());
                  for(int i = 0; i < authorList.size(); i++){
                      Annotation author = (Annotation)authorList.get(i);
                      FeatureMap feats = author.getFeatures();
                      feats.put("employedBy", affiliationList.get(i).getId());
                      //System.out.println("updated: " + feats);
                  }
              }else{
                  System.out.println("Uncertain matching!");
                  List<Annotation> affiliationCopy = affiliationList;
                  //List<Annotation> affiliationCopy = new ArrayList<Annotation>(affiliationList.size());
                  
                  for(int i = 0; i < authorList.size(); i++){
                      Annotation author = (Annotation)authorList.get(i);
                      FeatureMap feats = author.getFeatures();
                      
                      /*Iterator<Annotation> iter = affiliationCopy.listIterator();
                       if(iter.hasNext()){
                       Annotation aff = iter.next();
                       if(aff.getStartNode().getOffset() > author.getStartNode().getOffset()){
                       feats.put("employedBy", aff.getId());
                       iter.remove(); 
                       //System.out.println("updated: " + feats);
                       }
                       }*/
                      
                      
                      for(int j=0; j < affiliationCopy.size(); j++){
                          //System.out.println("Matching author " + author.getId() + " with " + affiliationCopy.get(j).getId());
                          if(affiliationCopy.get(j).getStartNode().getOffset() > author.getStartNode().getOffset()){
                              //Annotation match = affiliationCopy.remove(j);
                              System.out.println("affiliation seems to be after the author. Will use this one!");
                              feats.put("employedBy", affiliationCopy.get(j).getId());
                              break;
                              //System.out.println("updated: " + feats);
                          }
                      }
                  }
              }
              
          }
          System.out.println("Done!");
      }


} // class AuthorAffiliationInferer
