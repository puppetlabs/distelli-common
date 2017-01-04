package com.distelli.webserver;

public class JsonSuccess
{
    private boolean success = true;
    public static JsonSuccess Success = new JsonSuccess();

    public boolean getSuccess()
    {
        return success;
    }
}
