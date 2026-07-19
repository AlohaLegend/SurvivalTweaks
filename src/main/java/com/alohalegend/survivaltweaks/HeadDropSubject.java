package com.alohalegend.survivaltweaks;

import java.util.UUID;

record HeadDropSubject(UUID uuid, long playtimeHours, String address) {
    HeadDropSubject {
        address = address == null ? "" : address;
    }
}

