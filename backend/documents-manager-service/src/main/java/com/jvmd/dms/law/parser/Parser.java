package com.jvmd.dms.law.parser;

public interface Parser<T> {
    T parse(String url);
}
