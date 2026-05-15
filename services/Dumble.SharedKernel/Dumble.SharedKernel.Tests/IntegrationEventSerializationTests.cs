using System.Text.Json;
using System.Text.Json.Serialization;
using Dumble.SharedKernel.Common;
using Dumble.SharedKernel.Enums;
using Dumble.SharedKernel.Events.Chat;
using Dumble.SharedKernel.Events.Posts;
using Dumble.SharedKernel.Events.Social;
using System;
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
        // Capture before construction so the assertion is robust on slow CI —
        // a 5-second window can flake under heavy load. The bound here only
        // needs to be "the event's clock came from after we started this test".
        var before = DateTimeOffset.UtcNow;

        var evt = new UserFollowedEvent(
            FollowerId: "f1",
            FollowerName: "Carol",
            FollowerImage: null,
            FolloweeId: "u1",
            CreatedAt: DateTimeOffset.UtcNow);

        var after = DateTimeOffset.UtcNow;

        Assert.NotEqual(Guid.Empty, evt.EventId);
        Assert.InRange(evt.OccurredOn, before, after);
        Assert.Equal(1, evt.Version);
    }

    [Fact]
    public void CommentCreatedEvent_round_trips_with_optional_parent()
    {
        var original = new CommentCreatedEvent(
            CommentId: "c-1",
            PostId: "post-1",
            PostAuthorId: "author-1",
            CommentAuthorId: "commenter-1",
            CommenterName: "Dana",
            CommenterImage: null,
            ParentCommentAuthorId: "parent-author-1",
            Preview: "great post",
            CreatedAt: new DateTimeOffset(2026, 5, 7, 10, 0, 0, TimeSpan.Zero));

        var restored = JsonSerializer.Deserialize<CommentCreatedEvent>(
                JsonSerializer.Serialize(original, Options), Options)!;

        Assert.Equal(original.CommentId, restored.CommentId);
        Assert.Equal(original.PostAuthorId, restored.PostAuthorId);
        Assert.Equal(original.ParentCommentAuthorId, restored.ParentCommentAuthorId);
        Assert.Equal(original.Preview, restored.Preview);
        Assert.Equal(original.CreatedAt, restored.CreatedAt);
    }

    [Fact]
    public void CommentCreatedEvent_round_trips_when_parent_is_null()
    {
        var original = new CommentCreatedEvent(
            CommentId: "c-2",
            PostId: "post-2",
            PostAuthorId: "author-2",
            CommentAuthorId: "commenter-2",
            CommenterName: "Eve",
            CommenterImage: null,
            ParentCommentAuthorId: null,
            Preview: "first comment",
            CreatedAt: new DateTimeOffset(2026, 5, 7, 10, 0, 0, TimeSpan.Zero));

        var restored = JsonSerializer.Deserialize<CommentCreatedEvent>(
                JsonSerializer.Serialize(original, Options), Options)!;

        Assert.Null(restored.ParentCommentAuthorId);
    }

    [Fact]
    public void CommentDeletedEvent_round_trips()
    {
        var original = new CommentDeletedEvent(
            CommentId: "c-3",
            PostId: "post-3",
            PostAuthorId: "author-3",
            CommentAuthorId: "commenter-3");

        var restored = JsonSerializer.Deserialize<CommentDeletedEvent>(
                JsonSerializer.Serialize(original, Options), Options)!;

        Assert.Equal(original.CommentId, restored.CommentId);
        Assert.Equal(original.EventId, restored.EventId);
    }

    [Fact]
    public void PostDeletedEvent_round_trips()
    {
        var original = new PostDeletedEvent(PostId: "post-4", AuthorId: "author-4")
        {
            CorrelationId = "trace-del"
        };

        var restored = JsonSerializer.Deserialize<PostDeletedEvent>(
                JsonSerializer.Serialize(original, Options), Options)!;

        Assert.Equal("post-4", restored.PostId);
        Assert.Equal("author-4", restored.AuthorId);
        Assert.Equal("trace-del", restored.CorrelationId);
    }

    [Fact]
    public void ReactionRemovedEvent_round_trips()
    {
        var original = new ReactionRemovedEvent(
            PostId: "post-5",
            PostAuthorId: "author-5",
            ReactorId: "reactor-5");

        var restored = JsonSerializer.Deserialize<ReactionRemovedEvent>(
                JsonSerializer.Serialize(original, Options), Options)!;

        Assert.Equal(original.ReactorId, restored.ReactorId);
        Assert.Equal(original.OccurredOn, restored.OccurredOn);
    }

    [Fact]
    public void UserUnfollowedEvent_round_trips()
    {
        var original = new UserUnfollowedEvent(FollowerId: "f-6", FolloweeId: "u-6");

        var restored = JsonSerializer.Deserialize<UserUnfollowedEvent>(
                JsonSerializer.Serialize(original, Options), Options)!;

        Assert.Equal("f-6", restored.FollowerId);
        Assert.Equal("u-6", restored.FolloweeId);
        Assert.Equal(original.EventId, restored.EventId);
    }

    [Fact]
    public void MessageSentEvent_round_trips_with_empty_recipient_list()
    {
        // Edge case: a group conversation that briefly has no other live
        // recipients should still serialise/deserialise cleanly.
        var original = new MessageSentEvent(
            ConversationId: "conv-empty",
            SenderId: "sender-1",
            SenderName: "Bob",
            SenderImage: null,
            RecipientIds: Array.Empty<string>(),
            Preview: "hi",
            SentAt: DateTimeOffset.UtcNow);

        var restored = JsonSerializer.Deserialize<MessageSentEvent>(
                JsonSerializer.Serialize(original, Options), Options)!;

        Assert.Empty(restored.RecipientIds);
    }

    [Fact]
    public void PostReactedEvent_round_trips_with_non_ascii_name()
    {
        // Reactor names can contain non-ASCII (Arabic, emoji, etc.) — the
        // serializer must preserve them through the envelope unchanged.
        var original = new PostReactedEvent(
            PostId: "post-uni",
            PostAuthorId: "author-uni",
            ReactorId: "reactor-uni",
            ReactorName: "محمد 💪",
            ReactorImage: null,
            ReactionType: ReactionType.Support,
            CreatedAt: DateTimeOffset.UtcNow);

        var restored = JsonSerializer.Deserialize<PostReactedEvent>(
                JsonSerializer.Serialize(original, Options), Options)!;

        Assert.Equal("محمد 💪", restored.ReactorName);
        Assert.Equal(ReactionType.Support, restored.ReactionType);
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
