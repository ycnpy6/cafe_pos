package com.cafepos.model;

public class User {
    private final int id;
    private final String name;
    private final String pinHash;
    private final UserRole role;

    public User(int id, String name, String pinHash, UserRole role) {
        this.id = id;
        this.name = name;
        this.pinHash = pinHash;
        this.role = role;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPinHash() {
        return pinHash;
    }

    public UserRole getRole() {
        return role;
    }
}
