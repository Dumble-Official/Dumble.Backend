using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Dumble.RecommendationService.Application.Contracts;

namespace Dumble.RecommendationService.Application.Authentication;

/// <summary>
/// Hand-rolls an HS256 JWT signed with the shared (base64) JWT secret. Sibling services validate
/// signature + lifetime only (ValidateIssuer/Audience = false), so a correctly-signed, unexpired
/// token with a userId claim is all that is needed — and hand-rolling avoids pulling a JWT-issuing
/// dependency into a service that otherwise only validates tokens. Pure, so it is unit-tested.
/// </summary>
public sealed class ServiceTokenProvider : IServiceTokenProvider
{
    private readonly ServiceAuthOptions _options;

    public ServiceTokenProvider(ServiceAuthOptions options) => _options = options;

    public string CreateToken()
    {
        var now = DateTimeOffset.UtcNow;
        var iat = now.ToUnixTimeSeconds();
        var exp = now.AddMinutes(Math.Max(1, _options.TokenLifetimeMinutes)).ToUnixTimeSeconds();

        var header = Encode(JsonSerializer.SerializeToUtf8Bytes(new Dictionary<string, object>
        {
            ["alg"] = "HS256",
            ["typ"] = "JWT"
        }));

        var payload = Encode(JsonSerializer.SerializeToUtf8Bytes(new Dictionary<string, object>
        {
            ["sub"] = _options.ServiceUserId,
            ["userId"] = _options.ServiceUserId,
            ["role"] = "service",
            ["iat"] = iat,
            ["nbf"] = iat,
            ["exp"] = exp
        }));

        var signingInput = $"{header}.{payload}";
        using var hmac = new HMACSHA256(Convert.FromBase64String(_options.Secret));
        var signature = Encode(hmac.ComputeHash(Encoding.ASCII.GetBytes(signingInput)));

        return $"{signingInput}.{signature}";
    }

    private static string Encode(byte[] bytes) =>
        Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
}
