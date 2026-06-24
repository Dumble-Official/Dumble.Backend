package com.dumble.service.schedule.repository;

import com.dumble.service.schedule.domain.AuthorType;
import com.dumble.service.schedule.domain.ScheduleItem;
import com.dumble.service.schedule.domain.TableType;
import com.dumble.service.schedule.domain.Weekday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, UUID> {

    List<ScheduleItem> findByScheduleIdOrderByTableTypeAscWeekdayAscPositionAsc(UUID scheduleId);

    /** The items of a single (table, weekday) bucket — used to validate and re-stamp a reorder. */
    List<ScheduleItem> findByScheduleIdAndTableTypeAndWeekday(UUID scheduleId, TableType tableType, Weekday weekday);

    /**
     * Next append position for a (table, weekday) bucket: max(position)+1, so
     * positions stay strictly increasing even after deletions (a plain count
     * would reuse a freed position and collide).
     */
    @Query("select coalesce(max(i.position), -1) + 1 from ScheduleItem i "
            + "where i.scheduleId = :scheduleId and i.tableType = :tableType and i.weekday = :weekday")
    int nextPosition(@Param("scheduleId") UUID scheduleId,
                     @Param("tableType") TableType tableType,
                     @Param("weekday") Weekday weekday);

    /** Used by the chatbot apply with replace=true to clear only its own prior items. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ScheduleItem i where i.scheduleId = :scheduleId and i.authorType = :authorType")
    int deleteByScheduleAndAuthor(@Param("scheduleId") UUID scheduleId, @Param("authorType") AuthorType authorType);
}
