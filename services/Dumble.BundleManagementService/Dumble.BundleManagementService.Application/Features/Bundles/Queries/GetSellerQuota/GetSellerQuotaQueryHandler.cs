using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Application.Identity;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetSellerQuota;

internal sealed class GetSellerQuotaQueryHandler(
    IGenericRepository<Bundle, BundleId> bundlesRepository) : IRequestHandler<GetSellerQuotaQuery, GetSellerQuotaResult>
{
    // The tier-based max is owned by Subscription; until that is wired through, this is the
    // platform default ceiling a seller may have live at once.
    private const int MaxActiveBundlesPerSeller = 10;

    public async Task<GetSellerQuotaResult> Handle(GetSellerQuotaQuery request, CancellationToken cancellationToken)
    {
        var ownerId = AccountId.Create(AccountIdentity.ToAccountGuid(request.SellerId));

        var activeCount = await bundlesRepository.Count(
            b => b.OwnerId == ownerId && b.Status == Status.Published);

        return new GetSellerQuotaResult(
            activeCount,
            MaxActiveBundlesPerSeller,
            activeCount < MaxActiveBundlesPerSeller);
    }
}
