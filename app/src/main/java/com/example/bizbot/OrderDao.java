package com.example.bizbot;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(OrderEntity order);

    @Update
    void update(OrderEntity order);

    @Query("SELECT rowid, * FROM orders_table WHERE customerName = :name AND message = :msg LIMIT 1")
    OrderEntity findByContent(String name, String msg);

    @Query("SELECT rowid, * FROM orders_table WHERE isRead = 0 ORDER BY timestamp DESC")
    LiveData<List<OrderEntity>> getUnreadOrdersLive();

    @Query("SELECT rowid, * FROM orders_table WHERE isRead = 1 ORDER BY timestamp DESC")
    LiveData<List<OrderEntity>> getReadOrdersLive();

    @Query("UPDATE orders_table SET isRead = 1 WHERE rowid = :orderId")
    void markAsRead(int orderId);

    @Query("SELECT COUNT(*) FROM orders_table WHERE isRead = 0")
    LiveData<Integer> getUnreadCountLive();

    @Query("SELECT rowid, * FROM orders_table ORDER BY timestamp DESC")
    List<OrderEntity> getAllOrders();

    @Query("SELECT rowid, * FROM orders_table WHERE customerName = :name ORDER BY timestamp DESC LIMIT 1")
    OrderEntity getLatestOrderByName(String name);

    @Query("SELECT rowid, * FROM orders_table WHERE orders_table MATCH :query ORDER BY timestamp DESC")
    List<OrderEntity> searchOrders(String query);

    @Query("SELECT rowid, * FROM orders_table WHERE rowid = :orderId LIMIT 1")
    OrderEntity getOrderById(int orderId);

    @Query("SELECT rowid, * FROM orders_table WHERE rowid = :orderId LIMIT 1")
    LiveData<OrderEntity> getOrderByIdLive(int orderId);

    @Query("SELECT rowid, * FROM orders_table WHERE notificationKey = :key LIMIT 1")
    OrderEntity getOrderByKey(String key);
}
