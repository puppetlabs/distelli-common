/*
  $Id: $
  @file RequestContextFactory.java
  @brief Contains the RequestContextFactory.java class

  @author Rahul Singh [rsingh]
  Copyright (c) 2013, Distelli Inc., All Rights Reserved.
*/
package com.distelli.webserver;
import javax.servlet.http.HttpServletRequest;

@FunctionalInterface
public interface RequestContextFactory<R extends RequestContext>
{
    public R getRequestContext(HTTPMethod httpMethod, HttpServletRequest request);
}
