/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.fabric.commands;

import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.zookeeper.KeeperException;
import org.codehaus.jackson.map.ObjectMapper;
import org.fusesource.fabric.boot.commands.support.FabricCommand;
import org.fusesource.fabric.zookeeper.utils.ZooKeeperUtils;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Command(name = "cluster-list", scope = "fabric", description = "Lists all ActiveMQ message brokers in the fabric, enabling you to see which brokers are grouped into clusters.")
public class ClusterList extends FabricCommand {

    protected static String CLUSTER_PREFIX = "/fabric/registry/clusters";

    @Argument(required = false, description = "Path of the fabric registry node (Zookeeper registry node) to list. Relative paths are evaluated relative to the base node, /fabric/registry/clusters. If not specified, all clusters are listed.")
    String path = "";

    @Override
    protected Object doExecute() throws Exception {
        checkFabricAvailable();

        String realPath = path;
        if (!realPath.startsWith("/")) {
            realPath = CLUSTER_PREFIX;
            if (path.length() > 0) {
                realPath += "/" + path;
            }
        }
        printCluster(realPath, System.out);
        return null;
    }

    protected void printCluster(String dir, PrintStream out) throws InterruptedException, KeeperException, IOException, URISyntaxException {
        // do we have any clusters at all?
        if (getZooKeeper().exists(dir) == null) {
            return;
        }
        List<String> children = getZooKeeper().getAllChildren(dir);
        HashMap<String, HashMap<String,ClusterNode>> clusters = new HashMap<String, HashMap<String,ClusterNode>>();
        for (String child : children) {
            String childDir = dir + "/" + child;
            byte[] data = getZooKeeper().getData(childDir);
            if (data != null && data.length > 0) {
                String text = new String(data).trim();
                if (!text.isEmpty()) {
                    String clusterName = getClusterName(dir, childDir);
                    HashMap<String, ClusterNode> cluster = clusters.get(clusterName);
                    if (cluster == null) {
                        cluster = new HashMap<String, ClusterNode>();
                        clusters.put(clusterName, cluster);
                    }

                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> map = mapper.readValue(data, HashMap.class);

                    ClusterNode node = null;

                    Object id = value(map, "id", "container");
                    if (id != null) {
                        Object agent = value(map, "container", "agent");
                        List services = (List) value(map, "services");

                        node = cluster.get(id);
                        if (node == null) {
                            node = new ClusterNode();
                            cluster.put(id.toString(), node);
                        }

                        if (services != null) {
                            if (!services.isEmpty()) {
                                for (Object service : services) {
                                    node.services.add(ZooKeeperUtils.getSubstitutedData(getZooKeeper(), service.toString()));
                                }

                                node.masters.add(agent);
                            } else {
                                node.slaves.add(agent);
                            }
                        } else {
                            node.slaves.add(agent);
                        }
                    }
                }
            }
        }

        out.println(String.format("%-30s %-30s %-30s %s", "[cluster]", "[masters]", "[slaves]", "[services]"));

        for (String clusterName : clusters.keySet()) {
            HashMap<String, ClusterNode> nodes = clusters.get(clusterName);
            out.println(String.format("%-30s %-30s %-30s %s", clusterName, "", "", "", ""));
            for (String nodeName : nodes.keySet()) {
                ClusterNode node = nodes.get(nodeName);
                out.println(String.format("%-30s %-30s %-30s %s",
                            "   "  + nodeName,
                            printList(node.masters),
                            printList(node.slaves),
                            printList(node.services)));
            }
        }
    }

    protected String printList(List list) {
        if (list.isEmpty()) {
            return "-";
        }
        String text = list.toString();
        return text.substring(1, text.length() - 1);
    }

    protected Object value(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected String getClusterName(String rootDir, String dir) {
        String clusterName = dir;
        clusterName = clusterName.substring(0, clusterName.lastIndexOf("/"));
        if (clusterName.startsWith(rootDir)) {
            clusterName = clusterName.substring(rootDir.length());
        }
        if (clusterName.startsWith("/")) {
            clusterName = clusterName.substring(1);
        }
        if (clusterName.length() == 0) {
            clusterName = ".";
        }
        return clusterName;
    }

    protected class ClusterNode {
        public List masters = new ArrayList();
        public List services = new ArrayList();
        public List slaves = new ArrayList();

        @Override
        public String toString() {
            return masters + " " + services + " " + slaves;
        }
    }

}
