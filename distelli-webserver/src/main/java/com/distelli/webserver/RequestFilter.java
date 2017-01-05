/*
  $Id: $
  @file RequestFilter.java
  @brief Contains the RequestFilter.java class

  @author Rahul Singh [rsingh]
*/
package com.distelli.webserver;

public interface RequestFilter<R extends RequestContext>
{
    public WebResponse filter(R requestContext, RequestFilterChain next);
}
