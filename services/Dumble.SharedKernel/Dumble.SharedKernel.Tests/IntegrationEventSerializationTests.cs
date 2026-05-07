using System.Text.Json;
using System.Text.Json.Serialization;
using Dumble.SharedKernel.Common;
using Dumble.SharedKernel.Enums;
using Dumble.SharedKernel.Events.Chat;
using Dumble.SharedKernel.Events.Posts;
using Dumble.SharedKernel.Events.Social;
using Xunit;

namespace Dumble.SharedKernel.Tests;

public class IntegrationEventSerializationTests
{
    private static readonly JsonSerializerOptions Options = new()
    {
        Converters = { new JsonStringEnumConverter() },
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    [Fact]
    public void PostReactedEvent_round_trips_with_envelope_and_enum()
    {
        var original = new PostReactedEvent(
            PostId: "post-1",
            PostAuthorId: "author-1",
            ReactorId: "reactor-1",
            ReactorName: "Alice",
            ReactorImage: null,
            ReactionType: ReactionType.Love,
            CreatedAt: new DateTimeOffset(2026, 5, 7, 10, 0, 0, TimeSpan.Zero))
        {
            CorrelationId = "trace-abc"
        };

        var json = JsonSerializer.Serialize(original, Options);
        var restored = JsonSerializer.Deserialize<PostReactedEvent>(json, Options)!;

        Assert.Equal(original.EventId, restored.EventId);
        Assert.Equal(original.OccurredOn, restored.OccurredOn);
        Assert.Equal("trace-abc", restored.CorrelationId);
        Assert.Equal(ReactionType.Love, restored.ReactionType);
        Assert.Equal(original.CreatedAt, restored.CreatedAt);
        Assert.Contains("\"reactionType\":\"Love\"", json);
    }

    [Fact]
    public void PostCreatedEvent_authorType_is_string_on_the_wire()
    {
        var original = new PostCreatedEvent(
            PostId: "post-1",
            AuthorId: "author-1",
            AuthorType: UserType.GymOwner,
            GymId: "gym-1",
            Hashtags: new[] { "fit", "lift" },
            CreatedAt: DateTimeOffset.UtcNow);

        var json = JsonSerializer.Serialize(original, Options);
        var restored = JsonSerializer.Deserialize<PostCreatedEvent>(json, Options)!;

        Assert.Equal(UserType.GymOwner, restored.AuthorType);
        Assert.Contains("\"authorType\":\"GymOwner\"", json);
        Assert.Equal(2, restored.Hashtags.Count);
    }

    [Fact]
    public void MessageSentEvent_recipient_list_round_trips()
    {
        var original = new MessageSentEvent(
            ConversationId: "conv-1",
            SenderId: "sender-1",
            SenderName: "Bob",
            SenderImage: "https://cdn.test/bob.png",
            RecipientIds: new[] { "u1", "u2", "u3" },
            Preview: "hello",
            SentAt: DateTimeOffset.UtcNow);

        var json = JsonSerializer.Serialize(original, Options);
        var restored = JsonSerializer.Deserialize<MessageSentEvent>(json, Options)!;

        Assert.Equal(3, restored.RecipientIds.Count);
        Assert.Equal("u2", restored.RecipientIds[1]);
        Assert.Equal(original.SenderImage, restored.SenderImage);
    }

    [Fact]
    public void UserFollowedEvent_envelope_defaults_are_populated()
    {
        var evt = new UserFollowedEvent(
            FollowerId: "f1",
            FollowerName: "Carol",
            FollowerImage: null,
            FolloweeId: "u1",
            CreatedAt: DateTimeOffset.UtcNow);

        Assert.NotEqual(Guid.Empty, evt.EventId);
        Assert.True(evt.OccurredOn > DateTimeOffset.UtcNow.AddSeconds(-5));
        Assert.Equal(1, evt.Version);
    }

    [Fact]
    public void IntegrationEvent_envelope_supports_with_expressions()
    {
        IntegrationEvent original = new UserUnfollowedEvent("a", "b");
        var withCorrelation = original with { CorrelationId = "trace-xyz", Version = 2 };

        Assert.Null(original.CorrelationId);
        Assert.Equal("trace-xyz", withCorrelation.CorrelationId);
        Assert.Equal(2, withCorrelation.Version);
        Assert.Equal(original.EventId, withCorrelation.EventId);
    }
}
