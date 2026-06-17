using Dumble.RecommendationService.Domain.Outbox;
using Xunit;

namespace Dumble.RecommendationService.Tests.Outbox;

public class InteractionSignalMapperTests
{
    [Theory]
    [InlineData(InteractionSignal.View, OutboxOperation.AddDetailView)]
    [InlineData(InteractionSignal.Click, OutboxOperation.AddDetailView)]
    [InlineData(InteractionSignal.Dwell, OutboxOperation.AddDetailView)]
    [InlineData(InteractionSignal.Comment, OutboxOperation.AddBookmark)]
    [InlineData(InteractionSignal.ReactionRemoved, OutboxOperation.DeleteRating)]
    public void Maps_signal_to_expected_operation(InteractionSignal signal, OutboxOperation expected)
        => Assert.Equal(expected, InteractionSignalMapper.Map(signal).Operation);

    [Fact]
    public void Reaction_maps_to_positive_rating()
    {
        var mapping = InteractionSignalMapper.Map(InteractionSignal.Reaction);
        Assert.Equal(OutboxOperation.AddRating, mapping.Operation);
        Assert.Equal(InteractionSignalMapper.PositiveRating, mapping.RatingValue);
    }

    [Theory]
    [InlineData(InteractionSignal.View)]
    [InlineData(InteractionSignal.Click)]
    [InlineData(InteractionSignal.Dwell)]
    [InlineData(InteractionSignal.Comment)]
    [InlineData(InteractionSignal.ReactionRemoved)]
    public void Non_reaction_signals_carry_no_rating(InteractionSignal signal)
        => Assert.Null(InteractionSignalMapper.Map(signal).RatingValue);
}
