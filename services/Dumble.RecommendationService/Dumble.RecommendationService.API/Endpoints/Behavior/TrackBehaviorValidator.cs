using Dumble.RecommendationService.Contracts.Behavior;
using FastEndpoints;
using FluentValidation;

namespace Dumble.RecommendationService.API.Endpoints.Behavior;

public sealed class TrackBehaviorValidator : Validator<TrackBehaviorRequest>
{
    public TrackBehaviorValidator()
    {
        RuleFor(x => x.PostId).NotEmpty().MaximumLength(64);
        RuleFor(x => x.EventType).NotEmpty();
    }
}
