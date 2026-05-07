using Dumble.SharedKernel.Enums;

namespace Dumble.SharedKernel.Contracts;

public record CurrentUser(
    string Id,
    string Email,
    string DisplayName,
    string? ProfileImage,
    UserType UserType,
    IReadOnlyList<string> Roles
)
{
    public static CurrentUser Empty { get; } =
        new(string.Empty, string.Empty, string.Empty, null, UserType.Participant, Array.Empty<string>());
}
