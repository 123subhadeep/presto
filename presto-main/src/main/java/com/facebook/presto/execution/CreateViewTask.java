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
package com.facebook.presto.execution;

import com.facebook.presto.Session;
import com.facebook.presto.metadata.Metadata;
import com.facebook.presto.metadata.QualifiedObjectName;
import com.facebook.presto.metadata.ViewDefinition;
import com.facebook.presto.security.AccessControl;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.sql.analyzer.Analysis;
import com.facebook.presto.sql.analyzer.Analyzer;
import com.facebook.presto.sql.analyzer.FeaturesConfig;
import com.facebook.presto.sql.analyzer.QueryExplainer;
import com.facebook.presto.sql.parser.ParsingException;
import com.facebook.presto.sql.parser.SqlParser;
import com.facebook.presto.sql.tree.CreateView;
import com.facebook.presto.sql.tree.Query;
import com.facebook.presto.sql.tree.Statement;
import io.airlift.json.JsonCodec;

import javax.inject.Inject;

import java.util.List;
import java.util.Optional;

import static com.facebook.presto.metadata.MetadataUtil.createQualifiedObjectName;
import static com.facebook.presto.metadata.ViewDefinition.ViewColumn;
import static com.facebook.presto.spi.StandardErrorCode.INTERNAL_ERROR;
import static com.facebook.presto.sql.SqlFormatter.formatSql;
import static com.facebook.presto.util.ImmutableCollectors.toImmutableList;
import static java.util.Objects.requireNonNull;

public class CreateViewTask
        implements DataDefinitionTask<CreateView>
{
    private final JsonCodec<ViewDefinition> codec;
    private final SqlParser sqlParser;
    private final AccessControl accessControl;
    private final boolean experimentalSyntaxEnabled;

    @Inject
    public CreateViewTask(
            JsonCodec<ViewDefinition> codec,
            SqlParser sqlParser,
            AccessControl accessControl,
            FeaturesConfig featuresConfig)
    {
        this.codec = requireNonNull(codec, "codec is null");
        this.sqlParser = requireNonNull(sqlParser, "sqlParser is null");
        this.accessControl = requireNonNull(accessControl, "accessControl is null");
        requireNonNull(featuresConfig, "featuresConfig is null");
        this.experimentalSyntaxEnabled = featuresConfig.isExperimentalSyntaxEnabled();
    }

    @Override
    public String getName()
    {
        return "CREATE VIEW";
    }

    @Override
    public String explain(CreateView statement)
    {
        return "CREATE VIEW " + statement.getName();
    }

    @Override
    public void execute(CreateView statement, Session session, Metadata metadata, AccessControl accessControl, QueryStateMachine stateMachine)
    {
        QualifiedObjectName name = createQualifiedObjectName(session, statement, statement.getName());

        accessControl.checkCanCreateView(session.getIdentity(), name);

        String sql = getFormattedSql(statement);

        Analysis analysis = analyzeStatement(statement, session, metadata);

        List<ViewColumn> columns = analysis.getOutputDescriptor()
                .getVisibleFields().stream()
                .map(field -> new ViewColumn(field.getName().get(), field.getType()))
                .collect(toImmutableList());

        String data = codec.toJson(new ViewDefinition(sql, session.getCatalog(), session.getSchema(), columns, Optional.of(session.getUser())));

        metadata.createView(session, name, data, statement.isReplace());
    }

    private Analysis analyzeStatement(Statement statement, Session session, Metadata metadata)
    {
        Analyzer analyzer = new Analyzer(session, metadata, sqlParser, accessControl, Optional.<QueryExplainer>empty(), experimentalSyntaxEnabled);
        return analyzer.analyze(statement);
    }

    private String getFormattedSql(CreateView statement)
    {
        Query query = statement.getQuery();
        String sql = formatSql(query);

        // verify round-trip
        Statement parsed;
        try {
            parsed = sqlParser.createStatement(sql);
        }
        catch (ParsingException e) {
            throw new PrestoException(INTERNAL_ERROR, "Formatted query does not parse: " + query);
        }
        if (!query.equals(parsed)) {
            throw new PrestoException(INTERNAL_ERROR, "Query does not round-trip: " + query);
        }

        return sql;
    }
}
