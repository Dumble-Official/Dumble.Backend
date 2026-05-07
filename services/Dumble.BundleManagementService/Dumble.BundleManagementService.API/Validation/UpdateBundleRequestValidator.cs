using Dumble.BundleManagementService.Contracts.Bundles.UpdateBundle;
using FluentValidation;

namespace Dumble.BundleManagementService.API.Validation;

public sealed class UpdateBundleRequestValidator : AbstractValidator<UpdateBundleRequest>
{
    private static readonly string[] AllowedStatuses = ["Draft", "Published", "Archived"];

    public UpdateBundleRequestValidator()
    {
        RuleFor(x => x.Id).NotEqual(Guid.Empty);
        When(x => x.Name is not null,
            () => RuleFor(x => x.Name!).NotEmpty().MaximumLength(200));
        When(x => x.Description is not null,
            () => RuleFor(x => x.Description!).NotEmpty().MaximumLength(2000));
        When(x => x.Price is not null,
            () => RuleFor(x => x.Price!.Value).GreaterThanOrEqualTo(0));
        When(x => x.Status is not null,
            () => RuleFor(x => x.Status!)
                .Must(s => AllowedStatuses.Contains(s, StringComparer.OrdinalIgnoreCase))
                .WithMessage("Status must be one of: Draft, Published, Archived"));
        When(x => x.ExpiresOn is not null,
            () => RuleFor(x => x.ExpiresOn!.Value).GreaterThan(DateTime.UtcNow));
        When(x => x.CategoryId is not null,
            () => RuleFor(x => x.CategoryId!.Value).NotEqual(Guid.Empty));
    }
}
