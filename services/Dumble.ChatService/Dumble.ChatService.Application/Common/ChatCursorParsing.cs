using System.Globalization;

namespace Dumble.ChatService.Application.Common;

internal static class ChatCursorParsing
{
    public static DateTime? ParseUtc(string? cursor)
    {
        if (string.IsNullOrEmpty(cursor)) return null;
        var parsed = DateTime.Parse(cursor, CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind);
        return parsed.Kind == DateTimeKind.Utc ? parsed : parsed.ToUniversalTime();
    }

    public static string Format(DateTime utc) =>
        DateTime.SpecifyKind(utc, DateTimeKind.Utc).ToString("O", CultureInfo.InvariantCulture);
}
