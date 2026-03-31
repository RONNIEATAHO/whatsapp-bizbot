package com.example.bizbot;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(OrderEntity order);

    @Query("SELECT rowid, * FROM orders_table ORDER BY timestamp DESC")
    LiveData<List<OrderEntity>> getAllOrdersLive();

    @Query("SELECT COUNT(*) FROM orders_table")
    LiveData<Integer> getOrderCountLive();

    @Query("SELECT rowid, * FROM orders_table ORDER BY timestamp DESC")
    List<OrderEntity> getAllOrders();

    @Query("SELECT rowid, * FROM orders_table WHERE orders_table MATCH :query ORDER BY timestamp DESC")
    List<OrderEntity> searchOrders(String query);

    @Query("SELECT rowid, * FROM orders_table WHERE rowid = :orderId LIMIT 1")
    OrderEntity getOrderById(int orderId);

    @Query("SELECT rowid, * FROM orders_table WHERE notificationKey = :key LIMIT 1")
    OrderEntity getOrderByKey(String key);
}
