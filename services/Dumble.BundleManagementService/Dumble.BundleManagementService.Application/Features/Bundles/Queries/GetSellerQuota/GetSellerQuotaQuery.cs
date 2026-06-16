using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetSellerQuota;

public sealed record GetSellerQuotaQuery(string SellerId) : IRequest<GetSellerQuotaResult>;

public sealed record GetSellerQuotaResult(int ActiveBundleCount, int MaxAllowed, bool CanCreateMore);
