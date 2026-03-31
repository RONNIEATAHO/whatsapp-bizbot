package com.example.bizbot;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Fts4;
import androidx.room.PrimaryKey;

@Fts4
@Entity(tableName = "orders_table_old") // Renamed to avoid conflict with OrderEntity
public class order {
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    public int id; 

    public String customerName;
    public String message;
    public long timestamp;
    public String notificationKey;
    public String phoneNumber;

    public order(String customerName, String message, long timestamp, String notificationKey, String phoneNumber) {
        this.customerName = customerName;
        this.message = message;
        this.timestamp = timestamp;
        this.notificationKey = notificationKey;
        this.phoneNumber = phoneNumber;
    }
}
