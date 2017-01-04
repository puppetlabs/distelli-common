/*
  $Id: $
  @file RequestFilter.java
  @brief Contains the RequestFilter.java class

  @author Rahul Singh [rsingh]
*/
package com.distelli.webserver;

public interface RequestFilter
{
    public WebResponse filter(RequestContext context, RequestFilterChain next);
}
