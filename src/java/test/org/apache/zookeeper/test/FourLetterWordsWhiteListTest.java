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


import org.apache.zookeeper.TestableZooKeeper;
import org.apache.zookeeper.common.X509Exception.SSLContextException;

import static org.apache.zookeeper.client.FourLetterWordMain.send4LetterWord;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FourLetterWordsWhiteListTest extends ClientBase {
    protected static final Logger LOG =
        LoggerFactory.getLogger(FourLetterWordsTest.class);

    @Rule
    public Timeout timeout = new Timeout(30000);

    // ZOOKEEPER-2693: test white list of four letter words.
    // For 3.5.x default white list is empty. Verify that is
    // the case (except 'stat' command which is enabled in ClientBase
    // which other tests depend on.).
    @Test
    public void testFourLetterWordsWhiteList() throws Exception {
        verify("stat", "Outstanding");
        verify("ruok", "");
        verify("envi", "");
        verify("conf", "");
        verify("srvr", "");
        verify("cons", "");
        verify("dump", "");
        verify("wchs", "");
        verify("wchp", "");
        verify("wchc", "");
        verify("srst", "");
        verify("crst", "");
        verify("stat", "");
        verify("srvr", "");
        verify("cons", "");
        verify("gtmk", "");
        verify("isro", "");

        TestableZooKeeper zk = createClient();
        String sid = getHexSessionId(zk.getSessionId());

        verify("stat", "Outstanding");
        verify("srvr", "");
        verify("cons", "");
        verify("dump", "");
        verify("dirs", "");

        zk.getData("/", true, null);

        verify("stat", "Outstanding");
        verify("srvr", "");
        verify("cons", "");
        verify("dump", "");

        verify("wchs", "");
        verify("wchp", "");
        verify("wchc", "");
        verify("dirs", "");
        zk.close();

        verify("ruok", "");
        verify("envi", "");
        verify("conf", "");
        verify("stat", "");
        verify("srvr", "");
        verify("cons", "");
        verify("dump", "");
        verify("wchs", "");
        verify("wchp", "");
        verify("wchc", "");

        verify("srst", "");
        verify("crst", "");

        verify("stat", "Outstanding");
        verify("srvr", "");
        verify("cons", "");
        verify("mntr", "");
        verify("mntr", "");
        verify("stat", "");
        verify("srvr", "");
        verify("dirs", "");
    }

    private String sendRequest(String cmd) throws IOException, SSLContextException {
      HostPort hpobj = ClientBase.parseHostPortList(hostPort).get(0);
      return send4LetterWord(hpobj.host, hpobj.port, cmd);
    }

    private void verify(String cmd, String expected) throws IOException, SSLContextException {
        String resp = sendRequest(cmd);
        LOG.info("cmd " + cmd + " expected " + expected + " got " + resp);
        Assert.assertTrue(resp.contains(expected));
    }
}
