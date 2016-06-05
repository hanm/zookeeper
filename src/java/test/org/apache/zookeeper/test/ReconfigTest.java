/**
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

package org.apache.zookeeper.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.PortAssignment;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZKTestCase;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.jmx.CommonNames;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.QuorumStats;
import org.apache.zookeeper.server.quorum.QuorumPeer.QuorumServer;
import org.apache.zookeeper.server.quorum.QuorumPeer.ServerState;
import org.apache.zookeeper.server.quorum.flexible.QuorumHierarchical;
import org.apache.zookeeper.server.quorum.flexible.QuorumMaj;
import org.apache.zookeeper.server.quorum.flexible.QuorumVerifier;
import org.junit.Assert;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReconfigTest extends ZKTestCase implements DataCallback{
    private static final Logger LOG = LoggerFactory
            .getLogger(ReconfigTest.class);

    private QuorumUtil qu;

    @After
    public void tearDown() throws Exception {
        if (qu != null) {
            qu.tearDown();
        }
    }

    public static String reconfig(ZooKeeper zk, List<String> joiningServers,
            List<String> leavingServers, List<String> newMembers, long fromConfig)
            throws KeeperException, InterruptedException {
        byte[] config = null;
        for (int j = 0; j < 30; j++) {
            try {
                config = zk.reconfig(joiningServers, leavingServers,
                        newMembers, fromConfig, new Stat());
                break;
            } catch (KeeperException.ConnectionLossException e) {
                if (j < 29) {
                    Thread.sleep(1000);
                } else {
                    // test fails if we still can't connect to the quorum after
                    // 30 seconds.
                    Assert.fail("client could not connect to reestablished quorum: giving up after 30+ seconds.");
                }
            }
        }

        String configStr = new String(config);
        if (joiningServers != null) {
            for (String joiner : joiningServers)
                Assert.assertTrue(configStr.contains(joiner));
        }
        if (leavingServers != null) {
            for (String leaving : leavingServers)
                Assert.assertFalse(configStr.contains("server.".concat(leaving)));
        }

        return configStr;
    }

    public static String testServerHasConfig(ZooKeeper zk,
            List<String> joiningServers, List<String> leavingServers)
            throws KeeperException, InterruptedException {
        byte[] config = null;
        for (int j = 0; j < 30; j++) {
            try {
                zk.sync("/", null, null);
                config = zk.getConfig(false, new Stat());
                break;
            } catch (KeeperException.ConnectionLossException e) {
                if (j < 29) {
                    Thread.sleep(1000);
                } else {
                    // test fails if we still can't connect to the quorum after
                    // 30 seconds.
                    Assert.fail("client could not connect to reestablished quorum: giving up after 30+ seconds.");
                }
            }

        }
        String configStr = new String(config);
        if (joiningServers != null) {
            for (String joiner : joiningServers) {
               Assert.assertTrue(configStr.contains(joiner));
            }
        }
        if (leavingServers != null) {
            for (String leaving : leavingServers)
                Assert.assertFalse(configStr.contains("server.".concat(leaving)));
        }

        return configStr;
    }
    
    public static void testNormalOperation(ZooKeeper writer, ZooKeeper reader)
            throws KeeperException, InterruptedException {
        boolean testNodeExists = false;
       
       for (int j = 0; j < 30; j++) {
            try {
               if (!testNodeExists) {
                   try{ 
                       writer.create("/test", "test".getBytes(),
                           ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                   } catch (KeeperException.NodeExistsException e) {                       
                   }
                   testNodeExists = true;
               }
                String data = "test" + j;
                writer.setData("/test", data.getBytes(), -1);
                reader.sync("/", null, null);
                byte[] res = reader.getData("/test", null, new Stat());
                if(!data.equals(new String(res))) {
                    String str = new String(res);
                    LOG.info(str);
                    Assert.fail();
                } else {
                    LOG.info("ok");
                }
                Assert.assertEquals(data, new String(res));
                break;
            } catch (KeeperException.ConnectionLossException e) {
                if (j < 29) {
                    Thread.sleep(1000);
                } else {
                    // test fails if we still can't connect to the quorum after
                    // 30 seconds.
                    Assert.fail("client could not connect to reestablished quorum: giving up after 30+ seconds.");
                }
            }

        }

    }    
    
    private int getLeaderId(QuorumUtil qu) {
        int leaderId = 1;
        while (qu.getPeer(leaderId).peer.leader == null)
            leaderId++;
        return leaderId;
    }

    public static ZooKeeper[] createHandles(QuorumUtil qu) throws IOException {
        // create an extra handle, so we can index the handles from 1 to qu.ALL
        // using the server id.
        ZooKeeper[] zkArr = new ZooKeeper[qu.ALL + 1];
        zkArr[0] = null; // not used.
        for (int i = 1; i <= qu.ALL; i++) {
            // server ids are 1, 2 and 3
            zkArr[i] = new ZooKeeper("127.0.0.1:"
                    + qu.getPeer(i).peer.getClientPort(),
                    ClientBase.CONNECTION_TIMEOUT, new Watcher() {
                        public void process(WatchedEvent event) {
                        }});
        }
        return zkArr;
    }

    public static void closeAllHandles(ZooKeeper[] zkArr) throws InterruptedException {
        for (ZooKeeper zk : zkArr)
            if (zk != null)
                zk.close();
    }

    @SuppressWarnings("unchecked")
    public void processResult(int rc, String path, Object ctx, byte[] data,
            Stat stat) {
        synchronized(ctx) {
            ((LinkedList<Integer>)ctx).add(rc);
            ctx.notifyAll();
        }
    }

    @Test
    public void testPortChange() throws Exception {
        qu = new QuorumUtil(1); // create 3 servers
        qu.disableJMXTest = true;
        qu.startAll();
        ZooKeeper[] zkArr = createHandles(qu);

        List<String> joiningServers = new ArrayList<String>();

        int leaderIndex = getLeaderId(qu);
        int followerIndex = leaderIndex == 1 ? 2 : 1;

        /*
        // modify follower's client port

        int quorumPort = qu.getPeer(followerIndex).peer.getQuorumAddress().getPort();
        int electionPort = qu.getPeer(followerIndex).peer.getElectionAddress().getPort(); 
        int oldClientPort = qu.getPeer(followerIndex).peer.getClientPort();
        int newClientPort = PortAssignment.unique();
        joiningServers.add("server." + followerIndex + "=localhost:" + quorumPort
                + ":" + electionPort + ":participant;localhost:" + newClientPort);

        // create a /test znode and check that read/write works before
        // any reconfig is invoked
        testNormalOperation(zkArr[followerIndex], zkArr[leaderIndex]);

        reconfig(zkArr[followerIndex], joiningServers, null, null, -1);

        try {
          for (int i=0; i < 20; i++) {
            Thread.sleep(1000);
            zkArr[followerIndex].setData("/test", "teststr".getBytes(), -1);
          }
        } catch (KeeperException.ConnectionLossException e) {
            Assert.fail("Existing client disconnected when client port changed!");
        }

        zkArr[followerIndex].close();
        zkArr[followerIndex] = new ZooKeeper("127.0.0.1:"
                + oldClientPort,
                ClientBase.CONNECTION_TIMEOUT, new Watcher() {
                    public void process(WatchedEvent event) {}});
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(1000);
                zkArr[followerIndex].setData("/test", "teststr".getBytes(), -1);
                Assert.fail("New client connected to old client port!");
            } catch (KeeperException.ConnectionLossException e) {
            }
        }

        zkArr[followerIndex].close();
        zkArr[followerIndex] = new ZooKeeper("127.0.0.1:"
                + newClientPort,
                ClientBase.CONNECTION_TIMEOUT, new Watcher() {
                    public void process(WatchedEvent event) {}});

        testNormalOperation(zkArr[followerIndex], zkArr[leaderIndex]);
        testServerHasConfig(zkArr[followerIndex], joiningServers, null);
        Assert.assertEquals(newClientPort, qu.getPeer(followerIndex).peer.getClientPort());

        joiningServers.clear();
*/

        // TODO: kill me.
        testNormalOperation(zkArr[followerIndex], zkArr[leaderIndex]);

        // change leader's leading port - should renounce leadership

        int newQuorumPort = PortAssignment.unique();
        joiningServers.add("server." + leaderIndex + "=localhost:"
                + newQuorumPort
                + ":"
                + qu.getPeer(leaderIndex).peer.getElectionAddress().getPort()
                + ":participant;localhost:"
                + qu.getPeer(leaderIndex).peer.getClientPort());

        reconfig(zkArr[leaderIndex], joiningServers, null, null, -1);

        testNormalOperation(zkArr[followerIndex], zkArr[leaderIndex]);

        Assert.assertTrue(qu.getPeer(leaderIndex).peer.getQuorumAddress()
                .getPort() == newQuorumPort);
        Assert.assertTrue(getLeaderId(qu) != leaderIndex); // the leader changed

        joiningServers.clear();

        /*
        // change everyone's leader election port

        for (int i = 1; i <= 3; i++) {
            joiningServers.add("server." + i + "=localhost:"
                    + qu.getPeer(i).peer.getQuorumAddress().getPort() + ":"
                    + PortAssignment.unique() + ":participant;localhost:"
                    + qu.getPeer(i).peer.getClientPort());
        }

        reconfig(zkArr[1], joiningServers, null, null, -1);

        leaderIndex = getLeaderId(qu);
        int follower1 = leaderIndex == 1 ? 2 : 1;
        int follower2 = 1;
        while (follower2 == leaderIndex || follower2 == follower1)
            follower2++;

        // lets kill the leader and see if a new one is elected

        qu.shutdown(getLeaderId(qu));

        testNormalOperation(zkArr[follower2], zkArr[follower1]);
        testServerHasConfig(zkArr[follower1], joiningServers, null);
        testServerHasConfig(zkArr[follower2], joiningServers, null);
        */

        closeAllHandles(zkArr);
    }

}
