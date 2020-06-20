package me.steinborn.minecraft.lotus.backend;

import java.lang.reflect.Field;

class Reflection {
    static Object getField(Object o, String field) throws IllegalAccessException, NoSuchFieldException {
        Field reflField = o.getClass().getDeclaredField(field);
        reflField.setAccessible(true);
        return reflField.get(o);
    }

    static void setField(Object o, String field, Object val) throws IllegalAccessException, NoSuchFieldException {
        Field reflField = o.getClass().getDeclaredField(field);
        reflField.setAccessible(true);
        reflField.set(o, val);
    }
}
