package com.bootsandcats.profilelite;

import io.micronaut.runtime.Micronaut;

/** Entry point for the Micronaut-based profile UI lite service. */
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
