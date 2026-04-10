using Dumble.SharedKernel.Enums;

namespace Dumble.SharedKernel.Contracts;

public record CurrentUser(
    string Id,
    string Email,
    string DisplayName,
    string? ProfileImage,
    UserType UserType
);
