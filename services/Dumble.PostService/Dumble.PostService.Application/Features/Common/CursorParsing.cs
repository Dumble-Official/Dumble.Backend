using System.Globalization;

namespace Dumble.PostService.Application.Features.Common;

internal static class CursorParsing
{
    /// <summary>
    /// Parse a client-supplied cursor as a strict ISO 8601 round-trip ("O")
    /// UTC timestamp. A malformed cursor returns null and is treated as
    /// "start from the top" by the consumer — DateTime.Parse would otherwise
    /// throw FormatException and surface a 500 / stack trace to the client.
    /// </summary>
    public static DateTime? ParseUtcCursor(string? cursor)
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

    public static string FormatCursor(DateTime utc) =>
        DateTime.SpecifyKind(utc, DateTimeKind.Utc).ToString("O", CultureInfo.InvariantCulture);
}
