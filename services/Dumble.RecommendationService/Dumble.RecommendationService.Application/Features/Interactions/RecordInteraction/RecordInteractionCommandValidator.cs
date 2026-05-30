using FluentValidation;

namespace Dumble.RecommendationService.Application.Features.Interactions.RecordInteraction;

public sealed class RecordInteractionCommandValidator : AbstractValidator<RecordInteractionCommand>
{
    public RecordInteractionCommandValidator()
    {
        RuleFor(x => x.UserId).NotEmpty().MaximumLength(64);
        RuleFor(x => x.ItemId).NotEmpty().MaximumLength(64);
        RuleFor(x => x.DurationSeconds)
            .GreaterThanOrEqualTo(0)
            .When(x => x.DurationSeconds.HasValue);
        RuleFor(x => x.SourceEventId)
            .MaximumLength(128)
            .When(x => x.SourceEventId is not null);
    }
}
