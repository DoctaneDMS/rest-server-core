/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.common.impl;

import java.util.function.Supplier;

/**
 *
 * @author jonathan
 */
public class CachedSupplier<T> implements Supplier<T> {
    
    private final Supplier<T> supplier;
    private T cache;
    
    public CachedSupplier(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public T get() {
        if (cache == null) cache = supplier.get();
        return cache;
    }
    
    public static <T> CachedSupplier<T> of(Supplier<T> toCache) { 
        return new CachedSupplier<T>(toCache); 
    }
}
