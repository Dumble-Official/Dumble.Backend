using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Accounts;

/// <summary>
/// Published by the Authentication service when a user account is deleted (right-to-be-forgotten).
/// Consumers purge whatever they hold about the user. Crosses Java -> .NET as raw JSON on the
/// "dumble.events" topic exchange with routing key "account.deleted", so the property names are
/// pinned to the Java (Jackson) camelCase wire format.
/// </summary>
public record AccountDeletedEvent(
    [property: JsonPropertyName("userId")] string UserId,
    [property: JsonPropertyName("deletedAt")] DateTimeOffset DeletedAt
) : IntegrationEvent;
