/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.server.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.zookeeper.server.DataTree;
import org.apache.zookeeper.test.ClientBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileToRocksDBSnapTest {
    private static final Logger LOG = LoggerFactory.getLogger(FileToRocksDBSnapTest.class);

    File tmpDir;

    @BeforeEach
    public void setUp() throws Exception {
        tmpDir = ClientBase.createEmptyTestDir();
    }

    @AfterEach
    public void tearDown() throws Exception {
        tmpDir = null;
    }

    @Test
    public void testMigrateFromFileToRocksDB() throws Exception {
        ConcurrentHashMap<Long, Integer> sessions = new ConcurrentHashMap<Long, Integer>();
        sessions.put((long) 1, 2001);
        sessions.put((long) 2, 2002);
        sessions.put((long) 3, 2003);
        DataTree dt = new DataTree();
        dt.createNode("/foo", "foof".getBytes(), null, 0, 0, 1, 1);

        // Use FileSnap to take a snapshot in file systems
        System.setProperty(SnapshotFactory.ZOOKEEPER_SNAPSHOT_NAME,
                "org.apache.zookeeper.server.persistence.FileSnap");
        FileTxnSnapLog snapLog = new FileTxnSnapLog(tmpDir, tmpDir);
        snapLog.save(dt, sessions, false);
        snapLog.close();

        // Use FileToRocksDBSnap to read the snapshot from file
        System.setProperty(SnapshotFactory.ZOOKEEPER_SNAPSHOT_NAME,
                "org.apache.zookeeper.server.persistence.FileToRocksDBSnap");
        sessions = new ConcurrentHashMap<Long, Integer>();
        dt = new DataTree();
        FileTxnSnapLog.PlayBackListener listener = mock(FileTxnSnapLog.PlayBackListener.class);
        snapLog = new FileTxnSnapLog(tmpDir, tmpDir);
        long result = snapLog.restore(dt, sessions, listener);

        assertTrue(sessions.get((long) 1) == 2001);
        assertTrue(sessions.get((long) 2) == 2002);
        assertTrue(sessions.get((long) 3) == 2003);
        assertTrue(sessions.keySet().size() == 3);
        assertTrue((int) result == 0);

        // Use FileToRocksDBSnap to take a snapshot in RocksDB
        snapLog.save(dt, sessions, false);
        snapLog.close();

        // Use RocksDBSnap to read the snapshot
        System.setProperty(SnapshotFactory.ZOOKEEPER_SNAPSHOT_NAME,
                "org.apache.zookeeper.server.persistence.RocksDBSnap");
        snapLog = new FileTxnSnapLog(tmpDir, tmpDir);
        sessions = new ConcurrentHashMap<Long, Integer>();
        dt = new DataTree();
        result = snapLog.restore(dt, sessions, listener);
        snapLog.close();

        assertTrue(sessions.get((long) 1) == 2001);
        assertTrue(sessions.get((long) 2) == 2002);
        assertTrue(sessions.get((long) 3) == 2003);
        assertTrue(sessions.keySet().size() == 3);
        assertTrue((int) result == 0);
    }
}