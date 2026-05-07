using Dumble.BundleManagementService.Contracts.Bundles.CreateBundle;
using FluentValidation;

namespace Dumble.BundleManagementService.API.Validation;

public sealed class CreateBundleRequestValidator : AbstractValidator<CreateBundleRequest>
{
    private static readonly string[] AllowedStatuses = ["Draft", "Published", "Archived"];

    public CreateBundleRequestValidator()
    {
        RuleFor(x => x.Name).NotEmpty().MaximumLength(200);
        RuleFor(x => x.Description).NotEmpty().MaximumLength(2000);
        RuleFor(x => x.Price).GreaterThanOrEqualTo(0);
        RuleFor(x => x.Status)
            .NotEmpty()
            .Must(s => AllowedStatuses.Contains(s, StringComparer.OrdinalIgnoreCase))
            .WithMessage("Status must be one of: Draft, Published, Archived");
        RuleFor(x => x.ExpiresOn).GreaterThan(DateTime.UtcNow);
        RuleFor(x => x.CategoryId).NotEqual(Guid.Empty);
    }
}
