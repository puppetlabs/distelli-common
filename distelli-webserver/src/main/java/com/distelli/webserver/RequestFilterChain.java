/*
  $Id: $
  @file RequestFilterChain.java
  @brief Contains the RequestFilterChain.java class

  @author Rahul Singh [rsingh]
*/
package com.distelli.webserver;

public interface RequestFilterChain
{
    public WebResponse filter(RequestContext requestContext);
}
