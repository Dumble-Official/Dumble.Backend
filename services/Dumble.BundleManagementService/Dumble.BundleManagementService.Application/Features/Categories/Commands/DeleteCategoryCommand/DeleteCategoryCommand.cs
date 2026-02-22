using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Categories.Commands.DeleteCategoryCommand;

public sealed record DeleteCategoryCommand(Guid Id) : IRequest;