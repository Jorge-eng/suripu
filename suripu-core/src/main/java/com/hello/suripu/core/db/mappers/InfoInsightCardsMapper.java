package com.hello.suripu.core.db.mappers;

import com.google.common.base.Optional;
import com.hello.suripu.core.models.Insights.InfoInsightCards;
import com.hello.suripu.core.models.Insights.InsightCard;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by kingshy on 1/15/15.
 */
public class InfoInsightCardsMapper implements ResultSetMapper<Optional<InfoInsightCards>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfoInsightCardsMapper.class);

    @Override
    public Optional<InfoInsightCards> map(int index, ResultSet r, StatementContext ctx) throws SQLException {

        try {
            InsightCard.Category category = InsightCard.Category.fromString(r.getString("category"));

            return Optional.of(new InfoInsightCards(r.getInt("id"),
                    category,
                    r.getString("title"),
                    r.getString("text"),
                    r.getString("image_url")));

        } catch (IllegalArgumentException exception) {
            LOGGER.debug("Illegal argument {} found in info_insight_cards", r.getString("category"));
        }
        return Optional.absent();
    }
}
