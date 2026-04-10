using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using MediatR;
using Microsoft.AspNetCore.Http;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.CreateBundle;

public sealed record CreateBundleCommand(
        IFormFileCollection? Images,
        string Name,
        string Description,
        decimal Price,
        string Status,
        DateTime ExpiresOn,
        Guid CategoryId
    ) : IRequest<BundleId>;