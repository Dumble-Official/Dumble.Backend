using Dumble.RecommendationService.Application.Features.Interactions.RecordInteraction;
using Dumble.RecommendationService.Domain.Outbox;
using Xunit;

namespace Dumble.RecommendationService.Tests.Features;

public class RecordInteractionCommandValidatorTests
{
    private readonly RecordInteractionCommandValidator _validator = new();

    private static RecordInteractionCommand Valid()
        => new("u1", "p1", InteractionSignal.View, DateTimeOffset.UnixEpoch);

    [Fact]
    public void Valid_command_passes()
        => Assert.True(_validator.Validate(Valid()).IsValid);

    [Fact]
    public void Empty_userId_fails()
    {
        var result = _validator.Validate(Valid() with { UserId = "" });
        Assert.False(result.IsValid);
        Assert.Contains(result.Errors, e => e.PropertyName == nameof(RecordInteractionCommand.UserId));
    }

    [Fact]
    public void Empty_itemId_fails()
    {
        var result = _validator.Validate(Valid() with { ItemId = "" });
        Assert.Contains(result.Errors, e => e.PropertyName == nameof(RecordInteractionCommand.ItemId));
    }

    [Fact]
    public void Overlong_userId_fails()
        => Assert.False(_validator.Validate(Valid() with { UserId = new string('x', 65) }).IsValid);

    [Fact]
    public void Negative_duration_fails()
    {
        var result = _validator.Validate(Valid() with { DurationSeconds = -1 });
        Assert.Contains(result.Errors, e => e.PropertyName == nameof(RecordInteractionCommand.DurationSeconds));
    }

    [Fact]
    public void Zero_duration_is_allowed()
        => Assert.True(_validator.Validate(Valid() with { DurationSeconds = 0 }).IsValid);
}
