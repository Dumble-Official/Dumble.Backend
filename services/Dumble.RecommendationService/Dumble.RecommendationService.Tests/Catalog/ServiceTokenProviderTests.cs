using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Dumble.RecommendationService.Application.Authentication;
using Xunit;

namespace Dumble.RecommendationService.Tests.Catalog;

public class ServiceTokenProviderTests
{
    // Arbitrary 32-byte base64 key, the same shape as a real JWT_SECRET.
    private const string Secret = "c2VjcmV0LWtleS1mb3ItdGVzdGluZy0zMmJ5dGVzLWxvbmch";

    private static ServiceTokenProvider Build() => new(new ServiceAuthOptions
    {
        Secret = Secret,
        ServiceUserId = "00000000-0000-4000-8000-0000000000a1",
        TokenLifetimeMinutes = 5
    });

    [Fact]
    public void Mints_a_three_part_jwt_with_a_valid_hs256_signature()
    {
        var token = Build().CreateToken();

        var parts = token.Split('.');
        Assert.Equal(3, parts.Length);

        // Recompute the signature over header.payload with the base64-decoded key — exactly what
        // a sibling service does to validate it. A match proves the token is accepted there.
        using var hmac = new HMACSHA256(Convert.FromBase64String(Secret));
        var expected = Convert.ToBase64String(hmac.ComputeHash(Encoding.ASCII.GetBytes($"{parts[0]}.{parts[1]}")))
            .TrimEnd('=').Replace('+', '-').Replace('/', '_');
        Assert.Equal(expected, parts[2]);
    }

    [Fact]
    public void Carries_userId_and_an_unexpired_lifetime()
    {
        var token = Build().CreateToken();
        var payload = JsonDocument.Parse(Decode(token.Split('.')[1])).RootElement;

        Assert.Equal("00000000-0000-4000-8000-0000000000a1", payload.GetProperty("userId").GetString());
        Assert.Equal("service", payload.GetProperty("role").GetString());

        var exp = payload.GetProperty("exp").GetInt64();
        var iat = payload.GetProperty("iat").GetInt64();
        Assert.True(exp > iat);
        Assert.True(exp > DateTimeOffset.UtcNow.ToUnixTimeSeconds());
    }

    private static byte[] Decode(string segment)
    {
        var s = segment.Replace('-', '+').Replace('_', '/');
        s = (s.Length % 4) switch { 2 => s + "==", 3 => s + "=", _ => s };
        return Convert.FromBase64String(s);
    }
}
