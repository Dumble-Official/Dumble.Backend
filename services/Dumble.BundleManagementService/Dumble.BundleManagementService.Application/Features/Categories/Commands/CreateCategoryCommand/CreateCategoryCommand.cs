using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Categories.Commands.CreateCategoryCommand;

public sealed record CreateCategoryCommand(string Name) : IRequest<CategoryId>;