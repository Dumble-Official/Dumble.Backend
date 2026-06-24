using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Schedule;

/// <summary>
/// Raw-JSON event published by the Java Schedule service on routing key
/// <c>schedule.reminder.due</c> when a user still has unmarked items for the
/// day. Field names match the Java <c>ReminderEvent</c> record exactly.
/// </summary>
public record ScheduleReminderDueEvent(
    [property: JsonPropertyName("userId")] Guid UserId,
    [property: JsonPropertyName("date")] string Date,
    [property: JsonPropertyName("pendingCount")] int PendingCount,
    [property: JsonPropertyName("timezone")] string Timezone
) : IntegrationEvent;
