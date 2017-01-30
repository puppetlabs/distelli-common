/*
  $Id: $
  @file AjaxHelperMap.java
  @brief Contains the AjaxHelperMap.java class

  @author Rahul Singh [rsingh]
  Copyright (c) 2013, Distelli Inc., All Rights Reserved.
*/
package com.distelli.webserver;

public interface AjaxHelperMap<R extends RequestContext>
{
    public AjaxHelper get(String operationName, R RequestContext);
}
