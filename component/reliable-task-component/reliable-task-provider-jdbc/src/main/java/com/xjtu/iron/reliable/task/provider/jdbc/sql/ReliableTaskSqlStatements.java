package com.xjtu.iron.reliable.task.provider.jdbc.sql;

import java.util.regex.Pattern;

/** 集中构造可靠任务 Repository 使用的固定表 SQL。 */
public final class ReliableTaskSqlStatements {

    public static final String DEFAULT_TABLE_NAME = "iron_reliable_task";

    private static final Pattern SAFE_TABLE =
            Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)?");
    private static final String COLUMNS =
            "store_name,namespace,task_id,task_type,business_key,payload,route_key,scan_bucket,status,"
                    + "attempt_count,max_attempts,next_execute_at,owner_id,lease_until,version,last_error_code,"
                    + "last_error_message,created_at,updated_at,completed_at";

    private final String table;

    public ReliableTaskSqlStatements(String table) {
        this.table = validateTable(table);
    }

    public String insert() {
        return "INSERT INTO " + table + " (" + COLUMNS + ")"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    }

    public String find() {
        return "SELECT " + COLUMNS + " FROM " + table
                + " WHERE store_name=? AND namespace=? AND task_id=?";
    }

    public String findDue() {
        return "SELECT " + COLUMNS + " FROM " + table
                + " WHERE store_name=? AND scan_bucket=? AND ("
                + "(status IN ('READY','RETRY_WAIT','WAIT_RECONCILE') AND next_execute_at<=?)"
                + " OR (status='RUNNING' AND lease_until<=?))"
                + " ORDER BY COALESCE(next_execute_at,lease_until),created_at LIMIT ?";
    }

    public String tryClaim(boolean expiredRunning) {
        String timeColumn = expiredRunning ? "lease_until" : "next_execute_at";
        return "UPDATE " + table
                + " SET status='RUNNING',owner_id=?,lease_until=?,next_execute_at=NULL,"
                + "attempt_count=CASE WHEN attempt_count < max_attempts THEN attempt_count+1 ELSE attempt_count END,"
                + "version=version+1,updated_at=?"
                + " WHERE store_name=? AND namespace=? AND task_id=? AND status=? AND version=?"
                + " AND " + timeColumn + "<=?";
    }

    public String transition() {
        return "UPDATE " + table
                + " SET status=?,next_execute_at=?,owner_id=NULL,lease_until=NULL,version=version+1,"
                + "last_error_code=?,last_error_message=?,updated_at=?,completed_at=?"
                + " WHERE store_name=? AND namespace=? AND task_id=?"
                + " AND status='RUNNING' AND owner_id=? AND version=?";
    }

    public String renewLease() {
        return "UPDATE " + table
                + " SET lease_until=?,updated_at=?"
                + " WHERE store_name=? AND namespace=? AND task_id=?"
                + " AND status='RUNNING' AND owner_id=? AND version=? AND lease_until>? AND lease_until<?";
    }

    public String adminTransition(boolean resetAttempts) {
        String attemptExpression = resetAttempts ? "0" : "attempt_count";
        return "UPDATE " + table
                + " SET status=?,next_execute_at=?,owner_id=NULL,lease_until=NULL,attempt_count="
                + attemptExpression
                + ",version=version+1,last_error_code=NULL,last_error_message=NULL,updated_at=?,completed_at=?"
                + " WHERE store_name=? AND namespace=? AND task_id=? AND status=? AND version=?";
    }

    private static String validateTable(String table) {
        if (table == null || !SAFE_TABLE.matcher(table.trim()).matches()) {
            throw new IllegalArgumentException("invalid reliable task table name: " + table);
        }
        return table.trim();
    }
}
