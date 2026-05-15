namespace Dumble.PostService.Infrastructure.Persistence.Repositories;

internal static class LikeEscaping
{
    // Escape user input before composing it into an ILIKE pattern. Postgres
    // treats %, _ and \ specially, so a query like "%" would otherwise match
    // every row (and "%a%a%a%" patterns are a cheap DoS vector).
    public static string EscapePattern(string value) =>
        value
            .Replace("\\", "\\\\")
            .Replace("%", "\\%")
            .Replace("_", "\\_");
}
