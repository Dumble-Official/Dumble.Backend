using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Application.Identity;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.CategoryAggregate;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetBundleQuery;

internal sealed class GetBundleQueryHandler(
    IGenericRepository<Bundle, BundleId> bundlesRepository,
    IGenericRepository<Category, CategoryId> categoryRepository) : IRequestHandler<GetBundleQuery, GetBundleResult>
{
    public async Task<GetBundleResult> Handle(GetBundleQuery request, CancellationToken cancellationToken)
    {
        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id))
            ?? throw new KeyNotFoundException($"Bundle {request.Id} not found");

        // Track view only when the viewer is authenticated — the endpoint
        // is AllowAnonymous so GetCurrentUser() would throw for unauthenticated
        // callers. Use the viewerExternalId passed by the endpoint instead.
        if (!string.IsNullOrWhiteSpace(request.ViewerExternalId))
        {
            var accountId = AccountId.Create(AccountIdentity.ToAccountGuid(request.ViewerExternalId));
            if (bundle.View(accountId))
            {
                bundlesRepository.Update(bundle);
                await bundlesRepository.CompleteAsync();
            }
        }

        var category = await categoryRepository.Get(bundle.CategoryId);

        // Duration in days from now until the bundle's expiry (the Subscription
        // service sets the subscription's endsAt = now + durationDays). At least 1.
        var durationDays = (int)Math.Max(1, (bundle.ExpiresOn.Date - DateTime.UtcNow.Date).TotalDays);

        return new GetBundleResult(
            bundle.Id.Value,
            bundle.Images.Select(i => i.Value).ToList(),
            bundle.Name.Value,
            bundle.Description.Value,
            bundle.Price.Value,
            bundle.ExpiresOn,
            bundle.Status.ToString(),
            bundle.Viewers.Count,
            category?.Name.Value ?? string.Empty,
            bundle.OwnerId.Value,
            bundle.OwnerType.ToString().ToUpperInvariant(),     // TRAINER | GYM
            (long)Math.Round(bundle.Price.Value * 100m),        // priceCents
            "EGP",
            durationDays,
            bundle.Status == Status.Published,                  // only a Published bundle is purchasable
            new List<string>()                                  // amenities not modelled in this service
        );
    }
}
