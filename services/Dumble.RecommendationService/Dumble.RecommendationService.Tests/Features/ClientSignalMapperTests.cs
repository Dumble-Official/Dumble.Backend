using Dumble.RecommendationService.Application.Features.Interactions;
using Dumble.RecommendationService.Domain.Outbox;
using Xunit;

namespace Dumble.RecommendationService.Tests.Features;

public class ClientSignalMapperTests
{
    [Theory]
    [InlineData("View", InteractionSignal.View)]
    [InlineData("view", InteractionSignal.View)]
    [InlineData("Click", InteractionSignal.Click)]
    [InlineData("TimeSpent", InteractionSignal.Dwell)]
    [InlineData("timespent", InteractionSignal.Dwell)]
    public void Parses_supported_client_signals(string eventType, InteractionSignal expected)
        => Assert.Equal(expected, ClientSignalMapper.Parse(eventType));

    [Theory]
    [InlineData("Reaction")]
    [InlineData("Comment")]
    [InlineData("Share")]
    [InlineData("")]
    [InlineData("nonsense")]
    public void Rejects_event_types_that_are_not_client_signals(string eventType)
        => Assert.Throws<ArgumentException>(() => ClientSignalMapper.Parse(eventType));
}
