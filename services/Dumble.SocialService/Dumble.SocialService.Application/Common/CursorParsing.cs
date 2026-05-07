using System.Globalization;

namespace Dumble.SocialService.Application.Common;

internal static class CursorParsing
{
    public static DateTime? ParseUtcCursor(string? cursor)
    {
        if (string.IsNullOrEmpty(cursor)) return null;
        var parsed = DateTime.Parse(cursor, CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind);
        return parsed.Kind == DateTimeKind.Utc ? parsed : parsed.ToUniversalTime();
    }

    public static string FormatCursor(DateTime utc) =>
        DateTime.SpecifyKind(utc, DateTimeKind.Utc).ToString("O", CultureInfo.InvariantCulture);
}
