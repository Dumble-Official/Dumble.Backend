using Dumble.PostService.Contracts.Comments;
using Dumble.PostService.Contracts.Posts;
using Dumble.PostService.Contracts.Reactions;
using FluentValidation;

namespace Dumble.PostService.API.Validation;

public sealed class CreatePostRequestValidator : AbstractValidator<CreatePostRequest>
{
    public CreatePostRequestValidator()
    {
        When(x => x.Content is not null,
            () => RuleFor(x => x.Content!).MaximumLength(5000));
        When(x => x.Hashtags is not null,
            () => RuleFor(x => x.Hashtags!).Must(h => h.Count <= 30)
                .WithMessage("At most 30 hashtags per post"));
        When(x => x.Images is not null,
            () => RuleFor(x => x.Images!).Must(i => i.Count <= 10)
                .WithMessage("At most 10 images per post"));
    }
}

public sealed class UpdatePostRequestValidator : AbstractValidator<UpdatePostRequest>
{
    public UpdatePostRequestValidator()
    {
        When(x => x.Content is not null,
            () => RuleFor(x => x.Content!).MaximumLength(5000));
        When(x => x.Hashtags is not null,
            () => RuleFor(x => x.Hashtags!).Must(h => h.Count <= 30)
                .WithMessage("At most 30 hashtags per post"));
    }
}

public sealed class CreateCommentRequestValidator : AbstractValidator<CreateCommentRequest>
{
    public CreateCommentRequestValidator()
    {
        RuleFor(x => x.Content).NotEmpty().MaximumLength(2000);
    }
}

public sealed class UpdateCommentRequestValidator : AbstractValidator<UpdateCommentRequest>
{
    public UpdateCommentRequestValidator()
    {
        RuleFor(x => x.Content).NotEmpty().MaximumLength(2000);
    }
}

public sealed class AddReactionRequestValidator : AbstractValidator<AddReactionRequest>
{
    private static readonly string[] AllowedTypes = ["Like", "Love", "Support", "Celebrate"];

    public AddReactionRequestValidator()
    {
        RuleFor(x => x.Type)
            .NotEmpty()
            .Must(t => AllowedTypes.Contains(t, StringComparer.OrdinalIgnoreCase))
            .WithMessage("Type must be one of: Like, Love, Support, Celebrate");
    }
}

public sealed class BatchGetPostsRequestValidator : AbstractValidator<BatchGetPostsRequest>
{
    public BatchGetPostsRequestValidator()
    {
        RuleFor(x => x.Ids)
            .NotNull()
            .Must(i => i.Count <= 100)
            .WithMessage("Cannot request more than 100 posts per batch");
    }
}
