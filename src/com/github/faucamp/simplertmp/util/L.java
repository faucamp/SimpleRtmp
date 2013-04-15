
package com.github.faucamp.simplertmp.util;

/**
 * Simple short-hand logging converter class; modify as necessary by whatever
 * logging mechanism you are using
 * 
 * @author francois
 */
public class L {
    
    public static boolean isDebugEnabled() {
        return true;
    }
    
    
    public static void t(String message) {        
    }
    
    public static void d(String message) {
        //System.out.println(message);
    }
    
    public static void i(String message) {
        System.out.println(message);
    }
    
    public static void w(String message) {
        System.out.println(message);
    }
    
    public static void w(String message, Throwable t) {
        System.out.println(message);
        t.printStackTrace();
    }
  
    public static void e(String message) {
        System.out.println(message);
    }
    
    public static void e(String message, Throwable t) {
        System.out.println(message);
        t.printStackTrace();
    }
    
}
