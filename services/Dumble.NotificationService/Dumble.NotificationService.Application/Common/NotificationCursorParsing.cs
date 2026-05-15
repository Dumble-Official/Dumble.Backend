using System.Globalization;

namespace Dumble.NotificationService.Application.Common;

internal static class NotificationCursorParsing
{
    /// <summary>
    /// Parse a client-supplied cursor as a strict ISO 8601 round-trip ("O")
    /// UTC timestamp. Malformed cursor returns null and is treated as
    /// "start from the top" — DateTime.Parse would otherwise throw
    /// FormatException and surface a 500 / stack trace to the client.
    /// </summary>
    public static DateTime? ParseUtc(string? cursor)
    {
        if (string.IsNullOrEmpty(cursor)) return null;
        if (!DateTime.TryParseExact(
                cursor,
                "O",
                CultureInfo.InvariantCulture,
                DateTimeStyles.RoundtripKind,
                out var parsed))
        {
            return null;
        }
        return parsed.Kind == DateTimeKind.Utc ? parsed : parsed.ToUniversalTime();
    }

    public static string Format(DateTime utc) =>
        DateTime.SpecifyKind(utc, DateTimeKind.Utc).ToString("O", CultureInfo.InvariantCulture);
}
