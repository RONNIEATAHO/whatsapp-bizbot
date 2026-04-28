package com.example.bizbot;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Fts4;
import androidx.room.PrimaryKey;

@Fts4
@Entity(tableName = "orders_table")
public class OrderEntity {
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    public int id; 

    public String customerName;
    public String message;
    public long timestamp;
    public String notificationKey;
    public String phoneNumber;
    public boolean isRead;
    public String locationData; // Stores coordinates or maps URL

    public OrderEntity(String customerName, String message, long timestamp, String notificationKey, String phoneNumber, boolean isRead, String locationData) {
        this.customerName = customerName;
        this.message = message;
        this.timestamp = timestamp;
        this.notificationKey = notificationKey;
        this.phoneNumber = phoneNumber;
        this.isRead = isRead;
        this.locationData = locationData;
    }
}
