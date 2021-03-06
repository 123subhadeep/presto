/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.tests.tpch;

import com.facebook.presto.spi.ConnectorFactory;
import com.facebook.presto.spi.NodeManager;
import com.facebook.presto.spi.Plugin;
import com.google.common.collect.ImmutableList;

import javax.inject.Inject;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class IndexedTpchPlugin
        implements Plugin
{
    private NodeManager nodeManager;
    private TpchIndexSpec indexSpec;

    public IndexedTpchPlugin(TpchIndexSpec indexSpec)
    {
        this.indexSpec = requireNonNull(indexSpec, "indexSpec is null");
    }

    public IndexedTpchPlugin()
    {
        this(TpchIndexSpec.NO_INDEXES);
    }

    @Inject
    public void setNodeManager(NodeManager nodeManager)
    {
        this.nodeManager = nodeManager;
    }

    @Override
    public <T> List<T> getServices(Class<T> type)
    {
        if (type == ConnectorFactory.class) {
            requireNonNull(nodeManager, "nodeManager is null");
            return ImmutableList.of(type.cast(new IndexedTpchConnectorFactory(nodeManager, indexSpec, 4)));
        }
        return ImmutableList.of();
    }
}
